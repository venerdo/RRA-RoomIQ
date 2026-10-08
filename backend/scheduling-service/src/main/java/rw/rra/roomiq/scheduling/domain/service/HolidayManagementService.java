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
import rw.rra.roomiq.scheduling.domain.dto.HolidayListQuery;
import rw.rra.roomiq.scheduling.domain.dto.HolidayPageResponse;
import rw.rra.roomiq.scheduling.domain.dto.HolidayResponse;
import rw.rra.roomiq.scheduling.domain.dto.SetHolidayRequest;
import rw.rra.roomiq.scheduling.domain.entity.Holiday;
import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;
import rw.rra.roomiq.scheduling.domain.repository.HolidayRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingCalendarRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class HolidayManagementService {
    private final HolidayRepository holidayRepository;
    private final WorkingCalendarRepository calendarRepository;

    public HolidayManagementService(HolidayRepository holidayRepository,
                                    WorkingCalendarRepository calendarRepository) {
        this.holidayRepository = holidayRepository;
        this.calendarRepository = calendarRepository;
    }

    public HolidayPageResponse list(HolidayListQuery query) {
        if (query.fromDate() != null && query.toDate() != null && query.toDate().isBefore(query.fromDate())) {
            throw invalid("INVALID_HOLIDAY_DATE_RANGE", "toDate must not be earlier than fromDate");
        }
        if (Boolean.TRUE.equals(query.nationwide())
                && (query.workingCalendarId() != null || query.officeBuildingId() != null)) {
            throw invalid("INVALID_HOLIDAY_SCOPE", "Nationwide filtering cannot include a calendar or building ID");
        }
        Specification<Holiday> specification = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.workingCalendarId() != null) {
                predicates.add(builder.equal(root.get("workingCalendar").get("id"), query.workingCalendarId()));
            }
            if (query.officeBuildingId() != null) {
                predicates.add(builder.equal(root.get("officeBuildingId"), query.officeBuildingId()));
            }
            if (Boolean.TRUE.equals(query.nationwide())) {
                predicates.add(builder.isNull(root.get("workingCalendar")));
                predicates.add(builder.isNull(root.get("officeBuildingId")));
            } else if (Boolean.FALSE.equals(query.nationwide())
                    && query.workingCalendarId() == null && query.officeBuildingId() == null) {
                predicates.add(builder.or(builder.isNotNull(root.get("workingCalendar")),
                        builder.isNotNull(root.get("officeBuildingId"))));
            }
            if (query.active() != null) {
                predicates.add(builder.equal(root.get("active"), query.active()));
            }
            if (query.blocksBooking() != null) {
                predicates.add(builder.equal(root.get("blocksBooking"), query.blocksBooking()));
            }
            if (query.fromDate() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("holidayDate"), query.fromDate()));
            }
            if (query.toDate() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("holidayDate"), query.toDate()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        String sortBy = query.sortBy() == null ? "holidayDate" : query.sortBy();
        String direction = query.sortDirection() == null ? "ASC" : query.sortDirection().toUpperCase(Locale.ROOT);
        Page<Holiday> page = holidayRepository.findAll(specification,
                PageRequest.of(query.pageNumber(), query.pageSize(), Sort.by(Sort.Direction.valueOf(direction), sortBy)));
        return new HolidayPageResponse(page.getContent().stream().map(HolidayResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), sortBy, direction);
    }

    public HolidayResponse get(UUID holidayId) {
        return HolidayResponse.from(requireHoliday(holidayId));
    }

    @Transactional
    public HolidayResponse create(SetHolidayRequest request, UUID actorUserId) {
        WorkingCalendar calendar = resolveCalendar(request);
        Holiday holiday = new Holiday(calendar, request.officeBuildingId(), request.holidayDate(),
                request.name().trim(), request.blocksBooking(), request.active(), actorUserId);
        return HolidayResponse.from(save(holiday));
    }

    @Transactional
    public HolidayResponse update(UUID holidayId, SetHolidayRequest request) {
        Holiday holiday = requireHoliday(holidayId);
        WorkingCalendar calendar = resolveCalendar(request);
        holiday.update(calendar, request.officeBuildingId(), request.holidayDate(), request.name().trim(),
                request.blocksBooking(), request.active());
        return HolidayResponse.from(save(holiday));
    }

    @Transactional
    public void delete(UUID holidayId) {
        holidayRepository.delete(requireHoliday(holidayId));
    }

    private WorkingCalendar resolveCalendar(SetHolidayRequest request) {
        if (request.workingCalendarId() == null) {
            return null;
        }
        WorkingCalendar calendar = calendarRepository.findById(request.workingCalendarId())
                .orElseThrow(() -> notFound("WORKING_CALENDAR_NOT_FOUND", "Working calendar"));
        UUID calendarBuildingId = calendar.getOfficeBuildingId();
        if (request.officeBuildingId() != null && calendarBuildingId != null
                && !request.officeBuildingId().equals(calendarBuildingId)) {
            throw invalid("HOLIDAY_SCOPE_MISMATCH", "The calendar does not belong to the specified office building");
        }
        return calendar;
    }

    private Holiday requireHoliday(UUID holidayId) {
        return holidayRepository.findById(holidayId)
                .orElseThrow(() -> notFound("HOLIDAY_NOT_FOUND", "Holiday"));
    }

    private Holiday save(Holiday holiday) {
        try {
            return holidayRepository.saveAndFlush(holiday);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "HOLIDAY_SCOPE_DATE_CONFLICT",
                    "A holiday already exists for this exact scope and date");
        }
    }

    private static DomainException notFound(String code, String label) {
        return new DomainException(HttpStatus.NOT_FOUND, code, label + " was not found");
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }
}