package rw.rra.roomiq.scheduling.domain.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.domain.dto.SetWorkingCalendarRequest;
import rw.rra.roomiq.scheduling.domain.dto.SetWorkingDayWindowRequest;
import rw.rra.roomiq.scheduling.domain.dto.WorkingCalendarListQuery;
import rw.rra.roomiq.scheduling.domain.dto.WorkingCalendarPageResponse;
import rw.rra.roomiq.scheduling.domain.dto.WorkingCalendarResponse;
import rw.rra.roomiq.scheduling.domain.dto.WorkingDayWindowResponse;
import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;
import rw.rra.roomiq.scheduling.domain.entity.WorkingDayWindow;
import rw.rra.roomiq.scheduling.domain.repository.WorkingCalendarRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingDayWindowRepository;
import rw.rra.roomiq.scheduling.domain.repository.HolidayRepository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkingCalendarManagementService {
    private final WorkingCalendarRepository calendarRepository;
    private final WorkingDayWindowRepository windowRepository;
    private final HolidayRepository holidayRepository;

    public WorkingCalendarManagementService(WorkingCalendarRepository calendarRepository,
                                            WorkingDayWindowRepository windowRepository,
                                            HolidayRepository holidayRepository) {
        this.calendarRepository = calendarRepository;
        this.windowRepository = windowRepository;
        this.holidayRepository = holidayRepository;
    }

    public WorkingCalendarPageResponse list(WorkingCalendarListQuery query) {
        Specification<WorkingCalendar> specification = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.search() != null && !query.search().isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("name")),
                        "%" + escapeLike(query.search().trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (query.officeBuildingId() != null) {
                predicates.add(builder.equal(root.get("officeBuildingId"), query.officeBuildingId()));
            }
            if (query.active() != null) {
                predicates.add(builder.equal(root.get("active"), query.active()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        String direction = query.sortDirection() == null ? "ASC" : query.sortDirection().toUpperCase(Locale.ROOT);
        Page<WorkingCalendar> page = calendarRepository.findAll(specification,
                PageRequest.of(query.pageNumber(), query.pageSize(), Sort.by(Sort.Direction.valueOf(direction), "name")));
        return new WorkingCalendarPageResponse(page.getContent().stream().map(WorkingCalendarResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), "name", direction);
    }

    public WorkingCalendarResponse get(UUID calendarId) {
        return WorkingCalendarResponse.from(requireCalendar(calendarId));
    }

    @Transactional
    public WorkingCalendarResponse create(SetWorkingCalendarRequest request) {
        String timezone = validateTimezone(request.timezone());
        if (request.officeBuildingId() != null && calendarRepository.existsByOfficeBuildingId(request.officeBuildingId())) {
            throw conflict("WORKING_CALENDAR_BUILDING_CONFLICT", "A working calendar already exists for this building");
        }
        WorkingCalendar calendar = new WorkingCalendar(request.name().trim(), request.officeBuildingId(), timezone,
                request.defaultCalendar(), request.active());
        return WorkingCalendarResponse.from(save(calendar));
    }

    @Transactional
    public WorkingCalendarResponse update(UUID calendarId, SetWorkingCalendarRequest request) {
        WorkingCalendar calendar = requireCalendar(calendarId);
        String timezone = validateTimezone(request.timezone());
        UUID requestedBuildingId = request.officeBuildingId();
        if (requestedBuildingId != null && !requestedBuildingId.equals(calendar.getOfficeBuildingId())
                && calendarRepository.existsByOfficeBuildingIdAndIdNot(requestedBuildingId, calendarId)) {
            throw conflict("WORKING_CALENDAR_BUILDING_CONFLICT", "A working calendar already exists for this building");
        }
        calendar.update(request.name().trim(), requestedBuildingId, timezone,
                request.defaultCalendar(), request.active());
        return WorkingCalendarResponse.from(save(calendar));
    }

    @Transactional
    public void delete(UUID calendarId) {
        WorkingCalendar calendar = requireCalendar(calendarId);
        if (windowRepository.existsByWorkingCalendar_Id(calendarId)
            || holidayRepository.existsByWorkingCalendar_Id(calendarId)) {
            throw conflict("WORKING_CALENDAR_IN_USE",
                "Delete referencing working-day windows and holidays before deleting the calendar");
        }
        calendarRepository.delete(calendar);
    }

    public List<WorkingDayWindowResponse> listWindows(UUID calendarId) {
        requireCalendar(calendarId);
        return windowRepository.findAllByWorkingCalendar_IdOrderByDayOfWeek(calendarId).stream()
                .map(WorkingDayWindowResponse::from).toList();
    }

    @Transactional
    public WorkingDayWindowResponse createWindow(UUID calendarId, SetWorkingDayWindowRequest request) {
        WorkingCalendar calendar = requireCalendar(calendarId);
        validateWindow(request);
        return WorkingDayWindowResponse.from(windowRepository.saveAndFlush(new WorkingDayWindow(calendar,
                request.dayOfWeek(), request.openTime(), request.closeTime(), request.workingDay())));
    }

    @Transactional
    public WorkingDayWindowResponse updateWindow(UUID calendarId, UUID windowId,
                                                  SetWorkingDayWindowRequest request) {
        requireCalendar(calendarId);
        validateWindow(request);
        WorkingDayWindow window = windowRepository.findByIdAndWorkingCalendar_Id(windowId, calendarId)
                .orElseThrow(() -> notFound("WORKING_DAY_WINDOW_NOT_FOUND", "Working-day window"));
        window.update(request.dayOfWeek(), request.openTime(), request.closeTime(), request.workingDay());
        return WorkingDayWindowResponse.from(windowRepository.saveAndFlush(window));
    }

    @Transactional
    public void deleteWindow(UUID calendarId, UUID windowId) {
        requireCalendar(calendarId);
        WorkingDayWindow window = windowRepository.findByIdAndWorkingCalendar_Id(windowId, calendarId)
                .orElseThrow(() -> notFound("WORKING_DAY_WINDOW_NOT_FOUND", "Working-day window"));
        windowRepository.delete(window);
    }

    private WorkingCalendar requireCalendar(UUID calendarId) {
        return calendarRepository.findById(calendarId)
                .orElseThrow(() -> notFound("WORKING_CALENDAR_NOT_FOUND", "Working calendar"));
    }

    private WorkingCalendar save(WorkingCalendar calendar) {
        try {
            return calendarRepository.saveAndFlush(calendar);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("WORKING_CALENDAR_CONFLICT", "The working calendar conflicts with an existing record");
        }
    }

    private static String validateTimezone(String value) {
        String timezone = value.trim();
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE",
                    "Timezone must be a valid IANA zone ID");
        }
        return timezone;
    }

    private static void validateWindow(SetWorkingDayWindowRequest request) {
        if (request.dayOfWeek() < 1 || request.dayOfWeek() > 7) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_WORKING_DAY",
                    "dayOfWeek must be between 1 and 7");
        }
        if (request.openTime() == null || request.closeTime() == null
                || !request.closeTime().isAfter(request.openTime())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_WORKING_WINDOW",
                    "closeTime must be later than openTime");
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static DomainException notFound(String code, String label) {
        return new DomainException(HttpStatus.NOT_FOUND, code, label + " was not found");
    }

    private static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }
}