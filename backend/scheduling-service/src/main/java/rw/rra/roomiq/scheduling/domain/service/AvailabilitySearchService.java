package rw.rra.roomiq.scheduling.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.domain.dto.AvailabilitySearchRequest;
import rw.rra.roomiq.scheduling.domain.dto.AvailabilitySearchResponse;
import rw.rra.roomiq.scheduling.domain.dto.AvailabilityWindow;
import rw.rra.roomiq.scheduling.domain.entity.Holiday;
import rw.rra.roomiq.scheduling.domain.entity.RecurrenceRule;
import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;
import rw.rra.roomiq.scheduling.domain.entity.WorkingDayWindow;
import rw.rra.roomiq.scheduling.domain.recurrence.RecurrencePattern;
import rw.rra.roomiq.scheduling.domain.repository.BlockingClosureInterval;
import rw.rra.roomiq.scheduling.domain.repository.ClosurePeriodRepository;
import rw.rra.roomiq.scheduling.domain.repository.HolidayRepository;
import rw.rra.roomiq.scheduling.domain.repository.RecurrenceRuleRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingCalendarRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingDayWindowRepository;
import rw.rra.roomiq.scheduling.integration.BookingOccupancyClient;
import rw.rra.roomiq.scheduling.integration.BookingOccupancyClient.OccupancySnapshot;
import rw.rra.roomiq.scheduling.integration.BookingOccupancyClient.OccupiedInterval;
import rw.rra.roomiq.scheduling.integration.BookingOccupancyClient.RoomOccupancy;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient.MaintenancePeriodReference;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient.RoomFacilityReference;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient.RoomReference;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient.RoomRuleReference;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AvailabilitySearchService {
    private static final Duration MAX_SEARCH_RANGE = Duration.ofDays(31);
    private static final Duration MAX_OCCUPANCY_SNAPSHOT_AGE = Duration.ofSeconds(30);
    private static final Duration MAX_FUTURE_SNAPSHOT_SKEW = Duration.ofSeconds(5);
    private static final int MAX_CANDIDATE_WINDOWS = 5000;

    private final WorkingCalendarRepository calendarRepository;
    private final WorkingDayWindowRepository windowRepository;
    private final HolidayRepository holidayRepository;
    private final ClosurePeriodRepository closureRepository;
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final RoomAvailabilityClient roomClient;
    private final BookingOccupancyClient bookingClient;

    public AvailabilitySearchService(WorkingCalendarRepository calendarRepository,
                                     WorkingDayWindowRepository windowRepository,
                                     HolidayRepository holidayRepository,
                                     ClosurePeriodRepository closureRepository,
                                     RecurrenceRuleRepository recurrenceRuleRepository,
                                     RoomAvailabilityClient roomClient,
                                     BookingOccupancyClient bookingClient) {
        this.calendarRepository = calendarRepository;
        this.windowRepository = windowRepository;
        this.holidayRepository = holidayRepository;
        this.closureRepository = closureRepository;
        this.recurrenceRuleRepository = recurrenceRuleRepository;
        this.roomClient = roomClient;
        this.bookingClient = bookingClient;
    }

    public AvailabilitySearchResponse search(AvailabilitySearchRequest request) {
        validateRange(request.startsAt(), request.endsAt());
        WorkingCalendar calendar = calendarRepository.findById(request.workingCalendarId())
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "WORKING_CALENDAR_NOT_FOUND",
                        "Working calendar was not found"));
        validateCalendar(calendar, request.officeBuildingId());

        ZoneId zone = ZoneId.of(calendar.getTimezone());
        LocalDate fromDate = request.startsAt().atZone(zone).toLocalDate();
        LocalDate toDate = request.endsAt().atZone(zone).toLocalDate();
        if (request.endsAt().atZone(zone).toLocalTime().equals(LocalTime.MIDNIGHT)) {
            toDate = toDate.minusDays(1);
        }
        List<LocalDate> dates = searchDates(request.recurrenceRuleId(), fromDate, toDate, zone);
        Instant searchedAt = Instant.now();
        if (dates.isEmpty()) {
            return response(calendar, request.officeBuildingId(), searchedAt, null, false, List.of());
        }

        List<WorkingDayWindow> workingWindows = windowRepository
                .findAllByWorkingCalendar_IdOrderByDayOfWeek(calendar.getId());
        List<Holiday> holidays = holidayRepository
                .findAllByHolidayDateBetweenAndActiveTrueAndBlocksBookingTrue(fromDate, toDate);
        List<BlockingClosureInterval> closures = closureRepository.findBlockingOverlaps(
                request.officeBuildingId(), request.startsAt(), request.endsAt());
        List<RoomReference> rooms = roomClient.rooms(request.officeBuildingId());
        if (rooms == null) {
            throw roomUnavailable();
        }
        if (rooms.isEmpty()) {
            return response(calendar, request.officeBuildingId(), searchedAt, null, false, List.of());
        }

        List<RoomRuleReference> buildingRules = roomClient.buildingRules(request.officeBuildingId());
        if (buildingRules == null) {
            throw roomUnavailable();
        }
        validateRules(buildingRules, null, request.officeBuildingId());
        List<PreliminaryWindow> preliminary = new ArrayList<>();
        for (RoomReference room : rooms) {
            validateRoomReference(room, request.officeBuildingId());
            if (!"AVAILABLE".equals(room.status())
                    || (request.minimumCapacity() != null && room.capacity() < request.minimumCapacity())) {
                continue;
            }
            List<RoomRuleReference> roomRules = roomClient.roomRules(room.id());
            List<MaintenancePeriodReference> maintenance = roomClient.maintenancePeriods(room.id());
            validateRules(roomRules, room.id(), null);
            if (request.facilityTypeIds() != null && !request.facilityTypeIds().isEmpty()
                    && !workingFacilityIds(room.id()).containsAll(request.facilityTypeIds())) {
                continue;
            }

            for (LocalDate date : dates) {
                if (hasApplicableHoliday(date, calendar.getId(), request.officeBuildingId(), holidays)) {
                    continue;
                }
                List<TimeInterval> calendarIntervals = calendarIntervals(date, workingWindows, zone,
                        request.startsAt(), request.endsAt());
                if (calendarIntervals.isEmpty()) {
                    continue;
                }
                List<TimeInterval> busy = new ArrayList<>();
                addOverlapping(busy, maintenanceIntervals(maintenance), request.startsAt(), request.endsAt());
                addOverlapping(busy, closureIntervals(closures), request.startsAt(), request.endsAt());
                List<TimeInterval> free = subtract(calendarIntervals, busy);
                addRuleConstrainedWindows(preliminary, room, date, free, roomRules, buildingRules,
                        request, searchedAt, zone);
                if (preliminary.size() > MAX_CANDIDATE_WINDOWS) {
                    throw new DomainException(HttpStatus.BAD_REQUEST, "AVAILABILITY_RESULT_LIMIT_EXCEEDED",
                            "Availability search produced more than 5000 intervals; narrow the search range");
                }
            }
        }

        if (preliminary.isEmpty()) {
            return response(calendar, request.officeBuildingId(), searchedAt, null, false, List.of());
        }

        List<UUID> candidateRoomIds = preliminary.stream().map(window -> window.room().id()).distinct().toList();
        Instant occupancyRequestAt = Instant.now();
        OccupancySnapshot snapshot = bookingClient.occupancy(candidateRoomIds, request.startsAt(), request.endsAt());
        Map<UUID, List<TimeInterval>> occupiedByRoom = validateOccupancySnapshot(snapshot, candidateRoomIds,
                occupancyRequestAt, request.startsAt(), request.endsAt());
        List<AvailabilityWindow> available = new ArrayList<>();
        for (PreliminaryWindow candidate : preliminary) {
            List<TimeInterval> free = subtract(List.of(candidate.interval()),
                    occupiedByRoom.get(candidate.room().id()));
            for (TimeInterval interval : free) {
                if (Duration.between(interval.startsAt(), interval.endsAt()).toMinutes()
                        >= candidate.minimumDurationMinutes()) {
                    available.add(new AvailabilityWindow(candidate.room().id(), candidate.room().name(),
                            candidate.room().code(), candidate.room().capacity(), candidate.occurrenceDate(),
                            interval.startsAt(), interval.endsAt(), candidate.minimumDurationMinutes(),
                            candidate.maximumDurationMinutes(), request.recurrenceRuleId()));
                    if (available.size() > MAX_CANDIDATE_WINDOWS) {
                        throw new DomainException(HttpStatus.BAD_REQUEST, "AVAILABILITY_RESULT_LIMIT_EXCEEDED",
                                "Availability search produced more than 5000 intervals; narrow the search range");
                    }
                }
            }
        }
        available.sort(Comparator.comparing(AvailabilityWindow::startsAt)
                .thenComparing(AvailabilityWindow::roomId));
        return response(calendar, request.officeBuildingId(), searchedAt, snapshot.snapshotAt(), true,
                List.copyOf(available));
    }

    private Set<UUID> workingFacilityIds(UUID roomId) {
        List<RoomFacilityReference> facilities = roomClient.facilities(roomId);
        if (facilities == null) {
            throw roomUnavailable();
        }
        Set<UUID> ids = new HashSet<>();
        for (RoomFacilityReference facility : facilities) {
            if (facility == null || facility.facilityTypeId() == null || facility.state() == null
                    || !Set.of("WORKING", "FAULTY", "REMOVED").contains(facility.state())) {
                throw roomUnavailable();
            }
            if ("WORKING".equals(facility.state())) {
                ids.add(facility.facilityTypeId());
            }
        }
        return ids;
    }

    private void addRuleConstrainedWindows(List<PreliminaryWindow> results, RoomReference room, LocalDate date,
                                           List<TimeInterval> freeIntervals,
                                           List<RoomRuleReference> roomRules,
                                           List<RoomRuleReference> buildingRules,
                                           AvailabilitySearchRequest request, Instant searchedAt, ZoneId zone) {
        for (TimeInterval interval : freeIntervals) {
            List<Instant> boundaries = new ArrayList<>(List.of(interval.startsAt(), interval.endsAt()));
            addEffectiveBoundaries(boundaries, roomRules, interval);
            addEffectiveBoundaries(boundaries, buildingRules, interval);
            boundaries = boundaries.stream().distinct().sorted().toList();
            for (int index = 0; index + 1 < boundaries.size(); index++) {
                Instant start = boundaries.get(index);
                Instant end = boundaries.get(index + 1);
                RoomRuleReference rule = effectiveRule(roomRules, buildingRules, start);
                if (rule == null || (request.recurrenceRuleId() != null && !rule.recurringAllowed())) {
                    continue;
                }
                if (!departmentAllowed(rule, request.departmentId())) {
                    continue;
                }
                Instant earliestStart = max(start, searchedAt);
                if (rule.minAdvanceMinutes() != null) {
                    earliestStart = max(earliestStart, searchedAt.plus(rule.minAdvanceMinutes(), ChronoUnit.MINUTES));
                }
                Instant latestEnd = end;
                if (rule.maxAdvanceDays() != null) {
                    Instant maxAdvanceEnd = searchedAt.atZone(zone).toLocalDate()
                            .plusDays((long) rule.maxAdvanceDays() + 1)
                            .atStartOfDay(zone).toInstant();
                    latestEnd = min(latestEnd, maxAdvanceEnd);
                }
                int minimumDuration = maxPositive(rule.minDurationMinutes(), request.minimumDurationMinutes());
                if (!latestEnd.isAfter(earliestStart)
                        || (minimumDuration > 0
                        && Duration.between(earliestStart, latestEnd).toMinutes() < minimumDuration)) {
                    continue;
                }
                results.add(new PreliminaryWindow(room, date,
                        new TimeInterval(earliestStart, latestEnd), minimumDuration, rule.maxDurationMinutes()));
            }
        }
    }

    private static void addEffectiveBoundaries(List<Instant> boundaries, List<RoomRuleReference> rules,
                                               TimeInterval interval) {
        if (rules == null) {
            throw roomUnavailable();
        }
        for (RoomRuleReference rule : rules) {
            if (rule == null) {
                throw roomUnavailable();
            }
            Instant effectiveFrom = rule.effectiveFrom();
            if (effectiveFrom != null && effectiveFrom.isAfter(interval.startsAt())
                    && effectiveFrom.isBefore(interval.endsAt())) {
                boundaries.add(effectiveFrom);
            }
        }
    }

    private static RoomRuleReference effectiveRule(List<RoomRuleReference> roomRules,
                                                   List<RoomRuleReference> buildingRules, Instant at) {
        RoomRuleReference rule = latestActiveRule(roomRules, at);
        return rule != null ? rule : latestActiveRule(buildingRules, at);
    }

    private static RoomRuleReference latestActiveRule(List<RoomRuleReference> rules, Instant at) {
        List<RoomRuleReference> applicable = rules.stream()
                .filter(RoomRuleReference::active)
                .filter(rule -> rule.effectiveFrom() == null || !rule.effectiveFrom().isAfter(at))
                .toList();
        if (applicable.isEmpty()) {
            return null;
        }
        Instant latestEffectiveFrom = applicable.stream().map(rule -> rule.effectiveFrom() == null
                        ? Instant.MIN : rule.effectiveFrom())
                .max(Comparator.naturalOrder()).orElseThrow();
        List<RoomRuleReference> latest = applicable.stream()
                .filter(rule -> (rule.effectiveFrom() == null ? Instant.MIN : rule.effectiveFrom())
                        .equals(latestEffectiveFrom))
                .toList();
        if (latest.size() != 1) {
            throw roomUnavailable();
        }
        return latest.getFirst();
    }

    private static boolean departmentAllowed(RoomRuleReference rule, UUID departmentId) {
        List<UUID> allowed = rule.allowedDepartmentIds();
        if (allowed == null) {
            throw roomUnavailable();
        }
        return allowed.isEmpty() || (departmentId != null && allowed.contains(departmentId));
    }

    private List<LocalDate> searchDates(UUID recurrenceRuleId, LocalDate fromDate, LocalDate toDate, ZoneId zone) {
        if (recurrenceRuleId == null) {
            List<LocalDate> dates = new ArrayList<>();
            for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
                dates.add(date);
            }
            return List.copyOf(dates);
        }
        RecurrenceRule rule = recurrenceRuleRepository.findById(recurrenceRuleId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "RECURRENCE_RULE_NOT_FOUND",
                        "Recurrence rule was not found"));
        if (!rule.getTimezone().equals(zone.getId())) {
            throw invalid("RECURRENCE_TIMEZONE_MISMATCH",
                    "The recurrence timezone must match the working calendar timezone");
        }
        return RecurrencePattern.parse(rule.getRrule(), rule.getStartsOn()).occurrences().stream()
                .filter(date -> !date.isBefore(fromDate) && !date.isAfter(toDate))
                .toList();
    }

    private static List<TimeInterval> calendarIntervals(LocalDate date, List<WorkingDayWindow> windows, ZoneId zone,
                                                        Instant searchStart, Instant searchEnd) {
        if (windows == null) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "SCHEDULING_DATA_UNAVAILABLE",
                    "Working calendar windows are unavailable");
        }
        List<TimeInterval> intervals = new ArrayList<>();
        for (WorkingDayWindow window : windows) {
            if (window.isWorkingDay() && window.getDayOfWeek() == date.getDayOfWeek().getValue()) {
                Instant startsAt = date.atTime(window.getOpenTime()).atZone(zone).toInstant();
                Instant endsAt = date.atTime(window.getCloseTime()).atZone(zone).toInstant();
                TimeInterval clipped = intersect(new TimeInterval(startsAt, endsAt), searchStart, searchEnd);
                if (clipped != null) {
                    intervals.add(clipped);
                }
            }
        }
        return merge(intervals);
    }

    private static List<TimeInterval> maintenanceIntervals(List<MaintenancePeriodReference> periods) {
        if (periods == null) {
            throw roomUnavailable();
        }
        List<TimeInterval> result = new ArrayList<>();
        for (MaintenancePeriodReference period : periods) {
            if (period == null || period.period() == null) {
                throw roomUnavailable();
            }
            result.add(parseRange(period.period()));
        }
        return result;
    }

    private static TimeInterval parseRange(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < 5 || (trimmed.charAt(0) != '[' && trimmed.charAt(0) != '(')
                || (trimmed.charAt(trimmed.length() - 1) != ']' && trimmed.charAt(trimmed.length() - 1) != ')')) {
            throw roomUnavailable();
        }
        String[] endpoints = trimmed.substring(1, trimmed.length() - 1).split(",", -1);
        if (endpoints.length != 2) {
            throw roomUnavailable();
        }
        try {
            Instant startsAt = Instant.parse(unquote(endpoints[0].trim()));
            Instant endsAt = Instant.parse(unquote(endpoints[1].trim()));
            if (!endsAt.isAfter(startsAt)) {
                throw roomUnavailable();
            }
            return new TimeInterval(startsAt, endsAt);
        } catch (RuntimeException exception) {
            throw roomUnavailable();
        }
    }

    private static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static List<TimeInterval> closureIntervals(List<BlockingClosureInterval> closures) {
        if (closures == null) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "SCHEDULING_DATA_UNAVAILABLE",
                    "Blocking closure data is unavailable");
        }
        List<TimeInterval> result = new ArrayList<>();
        for (BlockingClosureInterval closure : closures) {
            if (closure == null || closure.getStartsAt() == null || closure.getEndsAt() == null
                    || !closure.getEndsAt().isAfter(closure.getStartsAt())) {
                throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "SCHEDULING_DATA_UNAVAILABLE",
                        "Blocking closure data is invalid");
            }
            result.add(new TimeInterval(closure.getStartsAt(), closure.getEndsAt()));
        }
        return result;
    }

    private static Map<UUID, List<TimeInterval>> validateOccupancySnapshot(
            OccupancySnapshot snapshot, List<UUID> roomIds, Instant requestAt, Instant searchStart, Instant searchEnd) {
        if (snapshot == null || snapshot.snapshotAt() == null || snapshot.rooms() == null) {
            throw bookingUnavailable();
        }
        Duration snapshotAge = Duration.between(snapshot.snapshotAt(), requestAt);
        if (snapshotAge.compareTo(MAX_OCCUPANCY_SNAPSHOT_AGE) > 0
                || snapshotAge.compareTo(MAX_FUTURE_SNAPSHOT_SKEW.negated()) < 0) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_OCCUPANCY_STALE",
                    "Booking occupancy snapshot is stale or has an invalid timestamp");
        }

        Set<UUID> requestedRooms = Set.copyOf(roomIds);
        Map<UUID, List<TimeInterval>> result = new HashMap<>();
        for (RoomOccupancy room : snapshot.rooms()) {
            if (room == null || room.roomId() == null || !requestedRooms.contains(room.roomId())
                    || room.intervals() == null || result.containsKey(room.roomId())) {
                throw bookingUnavailable();
            }
            List<TimeInterval> occupied = new ArrayList<>();
            for (OccupiedInterval interval : room.intervals()) {
                if (interval == null || interval.startsAt() == null || interval.endsAt() == null
                        || !interval.endsAt().isAfter(interval.startsAt())) {
                    throw bookingUnavailable();
                }
                TimeInterval clipped = intersect(new TimeInterval(interval.startsAt(), interval.endsAt()),
                        searchStart, searchEnd);
                if (clipped != null) {
                    occupied.add(clipped);
                }
            }
            result.put(room.roomId(), merge(occupied));
        }
        if (!result.keySet().equals(requestedRooms)) {
            throw bookingUnavailable();
        }
        return Map.copyOf(result);
    }

    private static void addOverlapping(List<TimeInterval> destination, List<TimeInterval> intervals,
                                       Instant startsAt, Instant endsAt) {
        for (TimeInterval interval : intervals) {
            TimeInterval clipped = intersect(interval, startsAt, endsAt);
            if (clipped != null) {
                destination.add(clipped);
            }
        }
    }

    private static List<TimeInterval> subtract(List<TimeInterval> source, List<TimeInterval> busyIntervals) {
        List<TimeInterval> busy = merge(busyIntervals);
        List<TimeInterval> free = new ArrayList<>();
        for (TimeInterval available : source) {
            Instant cursor = available.startsAt();
            for (TimeInterval occupied : busy) {
                if (!occupied.endsAt().isAfter(cursor)) {
                    continue;
                }
                if (!occupied.startsAt().isBefore(available.endsAt())) {
                    break;
                }
                if (occupied.startsAt().isAfter(cursor)) {
                    free.add(new TimeInterval(cursor, min(occupied.startsAt(), available.endsAt())));
                }
                cursor = max(cursor, occupied.endsAt());
                if (!cursor.isBefore(available.endsAt())) {
                    break;
                }
            }
            if (cursor.isBefore(available.endsAt())) {
                free.add(new TimeInterval(cursor, available.endsAt()));
            }
        }
        return free;
    }

    private static List<TimeInterval> merge(List<TimeInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        List<TimeInterval> sorted = intervals.stream().sorted(Comparator.comparing(TimeInterval::startsAt)).toList();
        List<TimeInterval> merged = new ArrayList<>();
        TimeInterval current = sorted.getFirst();
        for (int index = 1; index < sorted.size(); index++) {
            TimeInterval next = sorted.get(index);
            if (!next.startsAt().isAfter(current.endsAt())) {
                current = new TimeInterval(current.startsAt(), max(current.endsAt(), next.endsAt()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private static TimeInterval intersect(TimeInterval interval, Instant startsAt, Instant endsAt) {
        Instant start = max(interval.startsAt(), startsAt);
        Instant end = min(interval.endsAt(), endsAt);
        return end.isAfter(start) ? new TimeInterval(start, end) : null;
    }

    private static boolean hasApplicableHoliday(LocalDate date, UUID calendarId, UUID buildingId,
                                                List<Holiday> holidays) {
        return holidays.stream().filter(holiday -> holiday.getHolidayDate().equals(date))
                .anyMatch(holiday -> (holiday.getWorkingCalendar() == null
                                || holiday.getWorkingCalendar().getId().equals(calendarId))
                        && (holiday.getOfficeBuildingId() == null
                                || holiday.getOfficeBuildingId().equals(buildingId)));
    }

    private static void validateCalendar(WorkingCalendar calendar, UUID buildingId) {
        if (!calendar.isActive()) {
            throw invalid("WORKING_CALENDAR_INACTIVE", "The selected working calendar is inactive");
        }
        if (calendar.getOfficeBuildingId() != null && !calendar.getOfficeBuildingId().equals(buildingId)) {
            throw invalid("WORKING_CALENDAR_BUILDING_MISMATCH",
                    "The selected calendar does not belong to the requested office building");
        }
        try {
            if (!ZoneId.getAvailableZoneIds().contains(calendar.getTimezone())) {
                throw invalid("INVALID_TIMEZONE", "The working calendar timezone is not a recognized IANA zone");
            }
            ZoneId.of(calendar.getTimezone());
        } catch (RuntimeException exception) {
            throw invalid("INVALID_TIMEZONE", "The working calendar timezone is not a recognized IANA zone");
        }
    }

    private static void validateRange(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw invalid("INVALID_AVAILABILITY_RANGE", "endsAt must be later than startsAt");
        }
        if (Duration.between(startsAt, endsAt).compareTo(MAX_SEARCH_RANGE) > 0) {
            throw invalid("AVAILABILITY_RANGE_TOO_LARGE", "Availability searches are limited to 31 days");
        }
    }

    private static void validateRoomReference(RoomReference room, UUID buildingId) {
        if (room == null || room.id() == null || room.capacity() <= 0 || room.status() == null
                || room.name() == null || room.code() == null
                || room.officeBuildingId() == null || !room.officeBuildingId().equals(buildingId)) {
            throw roomUnavailable();
        }
    }

    private static void validateRules(List<RoomRuleReference> rules, UUID roomId, UUID buildingId) {
        if (rules == null) {
            throw roomUnavailable();
        }
        for (RoomRuleReference rule : rules) {
            if (rule == null || rule.id() == null || !java.util.Objects.equals(rule.roomId(), roomId)
                    || !java.util.Objects.equals(rule.officeBuildingId(), buildingId)
                    || (rule.minDurationMinutes() != null && rule.minDurationMinutes() < 1)
                    || (rule.maxDurationMinutes() != null && rule.maxDurationMinutes() < 1)
                    || (rule.minDurationMinutes() != null && rule.maxDurationMinutes() != null
                        && rule.minDurationMinutes() > rule.maxDurationMinutes())
                    || (rule.minAdvanceMinutes() != null && rule.minAdvanceMinutes() < 0)
                    || (rule.maxAdvanceDays() != null && rule.maxAdvanceDays() < 0)
                    || rule.allowedDepartmentIds() == null) {
                throw roomUnavailable();
            }
        }
    }

    private static AvailabilitySearchResponse response(WorkingCalendar calendar, UUID buildingId,
                                                       Instant searchedAt, Instant occupancySnapshotAt,
                                                       boolean occupancyIncluded, List<AvailabilityWindow> windows) {
        return new AvailabilitySearchResponse(calendar.getId(), buildingId, calendar.getTimezone(), searchedAt,
                occupancySnapshotAt, occupancyIncluded, true, windows);
    }

    private static int maxPositive(Integer first, Integer second) {
        return Math.max(first == null ? 0 : first, second == null ? 0 : second);
    }

    private static Instant max(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static DomainException roomUnavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_AVAILABILITY_UNAVAILABLE",
                "Current Room availability data is unavailable or invalid; no availability is being claimed");
    }

    private static DomainException bookingUnavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_OCCUPANCY_UNAVAILABLE",
                "Authoritative Booking occupancy is incomplete or invalid; no availability is being claimed");
    }

    private record TimeInterval(Instant startsAt, Instant endsAt) {
    }

    private record PreliminaryWindow(RoomReference room, LocalDate occurrenceDate, TimeInterval interval,
                                     int minimumDurationMinutes, Integer maximumDurationMinutes) {
    }
}
