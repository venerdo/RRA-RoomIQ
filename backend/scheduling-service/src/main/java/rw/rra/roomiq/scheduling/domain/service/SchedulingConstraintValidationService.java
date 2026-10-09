package rw.rra.roomiq.scheduling.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.domain.dto.SchedulingConstraintValidationResponse;
import rw.rra.roomiq.scheduling.domain.dto.SchedulingConstraintOccurrence;
import rw.rra.roomiq.scheduling.domain.dto.SchedulingConstraintViolation;
import rw.rra.roomiq.scheduling.domain.dto.ValidateSchedulingConstraintsRequest;
import rw.rra.roomiq.scheduling.domain.entity.Holiday;
import rw.rra.roomiq.scheduling.domain.entity.RecurrenceRule;
import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;
import rw.rra.roomiq.scheduling.domain.entity.WorkingDayWindow;
import rw.rra.roomiq.scheduling.domain.recurrence.RecurrencePattern;
import rw.rra.roomiq.scheduling.domain.repository.ClosurePeriodRepository;
import rw.rra.roomiq.scheduling.domain.repository.HolidayRepository;
import rw.rra.roomiq.scheduling.domain.repository.RecurrenceRuleRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingCalendarRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingDayWindowRepository;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SchedulingConstraintValidationService {
    private final WorkingCalendarRepository calendarRepository;
    private final WorkingDayWindowRepository windowRepository;
    private final HolidayRepository holidayRepository;
    private final ClosurePeriodRepository closureRepository;
    private final RecurrenceRuleRepository recurrenceRuleRepository;

    public SchedulingConstraintValidationService(WorkingCalendarRepository calendarRepository,
                                                 WorkingDayWindowRepository windowRepository,
                                                 HolidayRepository holidayRepository,
                                                 ClosurePeriodRepository closureRepository,
                                                 RecurrenceRuleRepository recurrenceRuleRepository) {
        this.calendarRepository = calendarRepository;
        this.windowRepository = windowRepository;
        this.holidayRepository = holidayRepository;
        this.closureRepository = closureRepository;
        this.recurrenceRuleRepository = recurrenceRuleRepository;
    }

    public SchedulingConstraintValidationResponse validate(ValidateSchedulingConstraintsRequest request) {
        ZoneId zone = zoneId(request.timezone());
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw invalid("INVALID_SCHEDULING_INTERVAL", "endsAt must be later than startsAt");
        }

        WorkingCalendar calendar = calendarRepository.findById(request.workingCalendarId())
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "WORKING_CALENDAR_NOT_FOUND",
                        "Working calendar was not found"));
        if (!calendar.getTimezone().equals(zone.getId())) {
            throw invalid("SCHEDULING_TIMEZONE_MISMATCH",
                    "The requested timezone must match the working calendar timezone");
        }
        LocalDateTime localStart = LocalDateTime.ofInstant(request.startsAt(), zone);
        LocalDateTime localEnd = LocalDateTime.ofInstant(request.endsAt(), zone);
        validateInterval(localStart, localEnd);

        List<LocalDate> occurrenceDates = occurrenceDates(request, localStart.toLocalDate(), zone);
        List<SchedulingConstraintViolation> violations = new ArrayList<>();
        if (!calendar.isActive()) {
            violations.add(violation(null, "WORKING_CALENDAR_INACTIVE",
                    "The selected working calendar is inactive"));
        }
        if (calendar.getOfficeBuildingId() != null
                && !calendar.getOfficeBuildingId().equals(request.officeBuildingId())) {
            violations.add(violation(null, "WORKING_CALENDAR_BUILDING_MISMATCH",
                    "The selected calendar does not belong to the requested office building"));
        }

        List<WorkingDayWindow> windows = windowRepository
                .findAllByWorkingCalendar_IdOrderByDayOfWeek(calendar.getId());
        LocalDate firstDate = occurrenceDates.getFirst();
        LocalDate lastDate = occurrenceDates.getLast();
        List<Holiday> holidays = holidayRepository
                .findAllByHolidayDateBetweenAndActiveTrueAndBlocksBookingTrue(firstDate, lastDate);
        List<SchedulingConstraintOccurrence> occurrenceIntervals = new ArrayList<>();

        for (LocalDate date : occurrenceDates) {
            validateWorkingWindow(date, localStart.toLocalTime(), localEnd.toLocalTime(), windows, violations);
            if (hasApplicableHoliday(date, calendar.getId(), request.officeBuildingId(), holidays)) {
                violations.add(violation(date, "BLOCKING_HOLIDAY",
                        "An active booking-blocking holiday applies to this date"));
            }

            ResolvedInterval interval = resolveInterval(date, firstDate, localStart.toLocalTime(),
                    localEnd.toLocalTime(), request, zone, violations);
            if (interval != null) {
                occurrenceIntervals.add(new SchedulingConstraintOccurrence(date,
                        interval.startsAt(), interval.endsAt()));
                if (closureRepository.existsBlockingOverlap(request.officeBuildingId(),
                        interval.startsAt(), interval.endsAt())) {
                    violations.add(violation(date, "BLOCKING_CLOSURE",
                            "A booking-blocking closure overlaps this occurrence"));
                }
            }
        }

        return new SchedulingConstraintValidationResponse(calendar.getId(), request.recurrenceRuleId(),
                zone.getId(), violations.isEmpty(), occurrenceDates.size(), List.copyOf(violations),
                List.copyOf(occurrenceIntervals));
    }

    private List<LocalDate> occurrenceDates(ValidateSchedulingConstraintsRequest request,
                                            LocalDate requestedStartDate, ZoneId requestedZone) {
        if (request.recurrenceRuleId() == null) {
            return List.of(requestedStartDate);
        }
        RecurrenceRule rule = recurrenceRuleRepository.findById(request.recurrenceRuleId())
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "RECURRENCE_RULE_NOT_FOUND",
                        "Recurrence rule was not found"));
        if (!rule.getTimezone().equals(requestedZone.getId())) {
            throw invalid("RECURRENCE_TIMEZONE_MISMATCH",
                    "The recurrence timezone must match the working calendar timezone");
        }
        if (!rule.getStartsOn().equals(requestedStartDate)) {
            throw invalid("RECURRENCE_START_DATE_MISMATCH",
                    "startsAt must fall on the recurrence rule's first occurrence date");
        }
        return RecurrencePattern.parse(rule.getRrule(), rule.getStartsOn()).occurrences();
    }

    private static void validateInterval(LocalDateTime localStart, LocalDateTime localEnd) {
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())
                || !localEnd.toLocalTime().isAfter(localStart.toLocalTime())) {
            throw invalid("INVALID_SCHEDULING_INTERVAL",
                    "The end must be later than the start and both must fall on the same local date");
        }
    }

    private static ZoneId zoneId(String timezone) {
        String normalized = timezone.trim();
        if (!ZoneId.getAvailableZoneIds().contains(normalized)) {
            throw invalid("INVALID_TIMEZONE", "timezone must be a recognized IANA zone ID");
        }
        try {
            return ZoneId.of(normalized);
        } catch (DateTimeException exception) {
            throw invalid("INVALID_TIMEZONE", "timezone must be a recognized IANA zone ID");
        }
    }

    private static void validateWorkingWindow(LocalDate date, LocalTime startsAt, LocalTime endsAt,
                                              List<WorkingDayWindow> windows,
                                              List<SchedulingConstraintViolation> violations) {
        boolean covered = windows.stream().anyMatch(window -> window.isWorkingDay()
                && window.getDayOfWeek() == date.getDayOfWeek().getValue()
                && !startsAt.isBefore(window.getOpenTime())
                && !endsAt.isAfter(window.getCloseTime()));
        if (!covered) {
            violations.add(violation(date, "OUTSIDE_WORKING_WINDOW",
                    "The occurrence must fit completely inside an active working-day window"));
        }
    }

    private static boolean hasApplicableHoliday(LocalDate date, UUID calendarId, UUID officeBuildingId,
                                                List<Holiday> holidays) {
        return holidays.stream().filter(holiday -> holiday.getHolidayDate().equals(date))
                .anyMatch(holiday -> (holiday.getWorkingCalendar() == null
                                || holiday.getWorkingCalendar().getId().equals(calendarId))
                        && (holiday.getOfficeBuildingId() == null
                                || holiday.getOfficeBuildingId().equals(officeBuildingId)));
    }

    private static ResolvedInterval resolveInterval(LocalDate date, LocalDate firstDate,
                                                   LocalTime startsAt, LocalTime endsAt,
                                                   ValidateSchedulingConstraintsRequest request,
                                                   ZoneId zone,
                                                   List<SchedulingConstraintViolation> violations) {
        LocalDateTime localStart = date.atTime(startsAt);
        LocalDateTime localEnd = date.atTime(endsAt);
        Instant startInstant;
        Instant endInstant;
        if (date.equals(firstDate)) {
            startInstant = request.startsAt();
            endInstant = request.endsAt();
        } else {
            List<ZoneOffset> startOffsets = zone.getRules().getValidOffsets(localStart);
            List<ZoneOffset> endOffsets = zone.getRules().getValidOffsets(localEnd);
            if (startOffsets.isEmpty() || endOffsets.isEmpty()) {
                violations.add(violation(date, "NONEXISTENT_LOCAL_TIME",
                        "A recurring local time does not exist because of a timezone transition"));
                return null;
            }
            startInstant = localStart.toInstant(startOffsets.getFirst());
            endInstant = localEnd.toInstant(endOffsets.getFirst());
        }
        if (!endInstant.isAfter(startInstant)) {
            violations.add(violation(date, "INVALID_OCCURRENCE_INTERVAL",
                    "The occurrence end must be later than its start in the selected timezone"));
            return null;
        }
        return new ResolvedInterval(startInstant, endInstant);
    }

    private static SchedulingConstraintViolation violation(LocalDate date, String code, String message) {
        return new SchedulingConstraintViolation(date, code, message);
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record ResolvedInterval(Instant startsAt, Instant endsAt) {
    }
}
