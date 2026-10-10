package rw.rra.roomiq.booking.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.common.web.DomainException;

import java.net.http.HttpClient;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

@Component
public class BookingOwnerServicesClient {
    private static final ParameterizedTypeReference<ApiResponse<BuildingReference>> BUILDING_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<DepartmentReference>> DEPARTMENT_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<RoomReference>> ROOM_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<RoomRuleReference>>> ROOM_RULES_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<MaintenancePeriodReference>>>
            MAINTENANCE_PERIODS_RESPONSE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<CalendarPage>> CALENDAR_PAGE_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<SchedulingDecision>> SCHEDULING_RESPONSE =
            new ParameterizedTypeReference<>() { };

    private final RestClient organizationClient;
    private final RestClient roomClient;
    private final RestClient schedulingClient;

    @Autowired
    public BookingOwnerServicesClient(
            @Value("${roomiq.organization.url:http://localhost:8082}") String organizationUrl,
            @Value("${roomiq.room.url:http://localhost:8083}") String roomUrl,
            @Value("${roomiq.scheduling.url:http://localhost:8084}") String schedulingUrl) {
        this(timeoutBoundBuilder(), organizationUrl, roomUrl, schedulingUrl);
    }

    BookingOwnerServicesClient(RestClient.Builder builder, String organizationUrl, String roomUrl,
                               String schedulingUrl) {
        organizationClient = builder.clone().baseUrl(organizationUrl).build();
        roomClient = builder.clone().baseUrl(roomUrl).build();
        schedulingClient = builder.clone().baseUrl(schedulingUrl).build();
    }

    public ValidatedBookingReferences validateRequest(BookingRequestFacts facts, String bearerToken, Instant now) {
        requireBearer(bearerToken);
        if (facts == null || now == null) {
            throw invalidRequest();
        }
        if (facts.departmentId() == null || facts.roomId() == null || facts.officeBuildingId() == null
                || facts.startsAt() == null || facts.endsAt() == null || facts.attendeeCount() < 1
                || !facts.endsAt().isAfter(facts.startsAt())) {
            throw invalidRequest();
        }

        BuildingReference building = get(organizationClient, "/api/v1/office-buildings/{id}",
                facts.officeBuildingId(), bearerToken, BUILDING_RESPONSE, "ORGANIZATION_SERVICE_UNAVAILABLE").data();
        if (building == null || !facts.officeBuildingId().equals(building.id()) || !building.active()) {
            throw invalidReference("The office building is missing or inactive");
        }
        if (building.timezone() == null || building.timezone().isBlank()) {
            throw unavailable("ORGANIZATION_SERVICE_UNAVAILABLE");
        }

        DepartmentReference department = get(organizationClient, "/api/v1/departments/{id}",
                facts.departmentId(), bearerToken, DEPARTMENT_RESPONSE, "ORGANIZATION_SERVICE_UNAVAILABLE").data();
        if (department == null || !facts.departmentId().equals(department.id())
                || !"ACTIVE".equals(department.status())
                || !facts.officeBuildingId().equals(department.officeBuildingId())) {
            throw invalidReference("The department is missing, inactive, or outside the selected building");
        }

        RoomReference room = get(roomClient, "/api/v1/rooms/{id}", facts.roomId(), bearerToken,
                ROOM_RESPONSE, "ROOM_SERVICE_UNAVAILABLE").data();
        if (room == null || !facts.roomId().equals(room.id()) || room.deletedAt() != null
                || !"AVAILABLE".equals(room.status())
                || !facts.officeBuildingId().equals(room.officeBuildingId())) {
            throw invalidReference("The room is missing, unavailable, or outside the selected building");
        }
        if (facts.attendeeCount() > room.capacity()) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "ROOM_CAPACITY_EXCEEDED",
                    "Attendee count exceeds the room capacity");
        }

        List<RoomRuleReference> roomRules = get(roomClient, "/api/v1/rooms/{roomId}/rules",
                facts.roomId(), bearerToken, ROOM_RULES_RESPONSE, "ROOM_SERVICE_UNAVAILABLE").data();
        List<RoomRuleReference> buildingRules = get(roomClient,
                "/api/v1/office-buildings/{officeBuildingId}/room-rules", facts.officeBuildingId(),
                bearerToken, ROOM_RULES_RESPONSE, "ROOM_SERVICE_UNAVAILABLE").data();

        UUID calendarId = resolveCalendarId(building, facts.officeBuildingId(), bearerToken);
        SchedulingDecision schedulingDecision = postSchedulingValidation(calendarId, facts, building.timezone(),
                bearerToken);
        if (schedulingDecision == null
                || !calendarId.equals(schedulingDecision.workingCalendarId())
                || !java.util.Objects.equals(facts.recurrenceRuleId(), schedulingDecision.recurrenceRuleId())
                || !building.timezone().equals(schedulingDecision.timezone())) {
            throw unavailable("SCHEDULING_SERVICE_UNAVAILABLE");
        }
        if (!schedulingDecision.valid()) {
            throw schedulingConstraintsInvalid(schedulingDecision.violations());
        }
        List<SchedulingOccurrence> occurrences = schedulingDecision.occurrenceIntervals();
        if (occurrences == null || occurrences.isEmpty()
                || occurrences.size() != schedulingDecision.occurrencesEvaluated()
                || occurrences.size() > 365
                || facts.recurrenceRuleId() == null && occurrences.size() != 1
                || occurrences.stream().anyMatch(java.util.Objects::isNull)
                || !facts.startsAt().equals(occurrences.getFirst().startsAt())
                || !facts.endsAt().equals(occurrences.getFirst().endsAt())
                || occurrences.stream().anyMatch(occurrence -> occurrence.occurrenceDate() == null
                        || occurrence.startsAt() == null || occurrence.endsAt() == null
                        || !occurrence.endsAt().isAfter(occurrence.startsAt()))) {
            throw unavailable("SCHEDULING_SERVICE_UNAVAILABLE");
        }
        ZoneId timezone;
        try {
            timezone = ZoneId.of(building.timezone());
        } catch (DateTimeException exception) {
            throw unavailable("SCHEDULING_SERVICE_UNAVAILABLE");
        }
        LocalDate firstDate = occurrences.getFirst().occurrenceDate();
        LocalTime localStart = occurrences.getFirst().startsAt().atZone(timezone).toLocalTime();
        LocalTime localEnd = occurrences.getFirst().endsAt().atZone(timezone).toLocalTime();
        for (int index = 1; index < occurrences.size(); index++) {
            SchedulingOccurrence previous = occurrences.get(index - 1);
            SchedulingOccurrence current = occurrences.get(index);
            if (!current.occurrenceDate().isAfter(previous.occurrenceDate())
                    || !current.startsAt().isAfter(previous.startsAt())
                    || current.occurrenceDate().isAfter(firstDate.plusYears(1))) {
                throw unavailable("SCHEDULING_SERVICE_UNAVAILABLE");
            }
        }

        boolean approvalRequired = false;
        int releaseBufferMinutes = 0;
        for (SchedulingOccurrence occurrence : occurrences) {
            LocalDate occurrenceStartDate = occurrence.startsAt().atZone(timezone).toLocalDate();
            LocalDate occurrenceEndDate = occurrence.endsAt().atZone(timezone).toLocalDate();
            if (!occurrence.occurrenceDate().equals(occurrenceStartDate)
                    || !occurrence.occurrenceDate().equals(occurrenceEndDate)
                    || !localStart.equals(occurrence.startsAt().atZone(timezone).toLocalTime())
                    || !localEnd.equals(occurrence.endsAt().atZone(timezone).toLocalTime())) {
                throw unavailable("SCHEDULING_SERVICE_UNAVAILABLE");
            }
            BookingRequestFacts occurrenceFacts = new BookingRequestFacts(facts.departmentId(), facts.roomId(),
                    facts.officeBuildingId(), facts.recurrenceRuleId(), occurrence.startsAt(), occurrence.endsAt(),
                    facts.attendeeCount(), facts.externalGuests());
            RoomRuleReference occurrenceRule = effectiveRule(roomRules, buildingRules, occurrence.startsAt());
            validateRoomPolicy(occurrenceFacts, occurrenceRule, now);
            approvalRequired |= occurrenceRule.approvalRequired();
            releaseBufferMinutes = Math.max(releaseBufferMinutes, occurrenceRule.releaseBufferMinutes());
        }
        List<MaintenancePeriodReference> maintenancePeriods = get(roomClient,
                "/api/v1/rooms/{roomId}/maintenance-periods", facts.roomId(), bearerToken,
                MAINTENANCE_PERIODS_RESPONSE, "ROOM_SERVICE_UNAVAILABLE").data();
        validateMaintenance(facts.roomId(), occurrences, releaseBufferMinutes, maintenancePeriods);

        return new ValidatedBookingReferences(room.officeBuildingId(), department.id(), room.id(),
                "VIP".equals(room.roomClass()), building.timezone(), approvalRequired,
                releaseBufferMinutes, List.copyOf(occurrences));
    }

    private static void validateMaintenance(UUID roomId, List<SchedulingOccurrence> occurrences,
                                            int releaseBufferMinutes,
                                            List<MaintenancePeriodReference> maintenancePeriods) {
        if (maintenancePeriods == null) {
            throw unavailable("ROOM_SERVICE_UNAVAILABLE");
        }
        if (maintenancePeriods.stream().anyMatch(period -> period == null || period.id() == null
                || !roomId.equals(period.roomId()) || period.period() == null)) {
            throw unavailable("ROOM_SERVICE_UNAVAILABLE");
        }
        List<TimeRange> ranges = maintenancePeriods.stream()
                .map(period -> parseMaintenanceRange(period.period()))
                .toList();
        TreeSet<LocalDate> conflicts = new TreeSet<>();
        for (SchedulingOccurrence occurrence : occurrences) {
            Instant occupiedUntil;
            try {
                occupiedUntil = occurrence.endsAt().plusSeconds(Math.multiplyExact((long) releaseBufferMinutes, 60));
            } catch (ArithmeticException | DateTimeException exception) {
                throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_INTERVAL_INVALID",
                        "The requested occupied interval exceeds the supported time range");
            }
            for (TimeRange range : ranges) {
                if (range == null) {
                    throw unavailable("ROOM_SERVICE_UNAVAILABLE");
                }
                if (occurrence.startsAt().isBefore(range.endsAt())
                        && range.startsAt().isBefore(occupiedUntil)) {
                    conflicts.add(occurrence.occurrenceDate());
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw occurrenceConflict("ROOM_MAINTENANCE_CONFLICT",
                    "Room maintenance overlaps occurrence dates: ", conflicts);
        }
    }

    private static TimeRange parseMaintenanceRange(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 5 || (trimmed.charAt(0) != '[' && trimmed.charAt(0) != '(')
                || (trimmed.charAt(trimmed.length() - 1) != ']' && trimmed.charAt(trimmed.length() - 1) != ')')) {
            return null;
        }
        String[] endpoints = trimmed.substring(1, trimmed.length() - 1).split(",", -1);
        if (endpoints.length != 2) {
            return null;
        }
        try {
            Instant startsAt = Instant.parse(unquote(endpoints[0].trim()));
            Instant endsAt = Instant.parse(unquote(endpoints[1].trim()));
            return endsAt.isAfter(startsAt) ? new TimeRange(startsAt, endsAt) : null;
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static DomainException schedulingConstraintsInvalid(List<SchedulingViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "SCHEDULING_CONSTRAINTS_INVALID",
                    "The request conflicts with the authoritative working calendar or scheduling policy");
        }
        String details = violations.stream()
                .filter(violation -> violation != null)
                .map(violation -> violation.occurrenceDate() == null ? violation.code()
                        : violation.occurrenceDate() + ":" + violation.code())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "SCHEDULING_CONSTRAINTS_INVALID",
                "Scheduling constraints failed for " + details);
    }

    private static DomainException occurrenceConflict(String code, String prefix, TreeSet<LocalDate> conflicts) {
        return new DomainException(HttpStatus.CONFLICT, code,
                prefix + conflicts.stream().map(LocalDate::toString)
                        .collect(java.util.stream.Collectors.joining(", ")));
    }

    private UUID resolveCalendarId(BuildingReference building, UUID officeBuildingId, String bearerToken) {
        if (building.workingCalendarId() != null) {
            return building.workingCalendarId();
        }
        CalendarPage calendars = get(schedulingClient,
                "/api/v1/working-calendars?officeBuildingId={buildingId}&active=true&page=0&size=100",
                officeBuildingId, bearerToken, CALENDAR_PAGE_RESPONSE, "SCHEDULING_SERVICE_UNAVAILABLE").data();
        if (calendars == null || calendars.items() == null || calendars.items().size() != 1
                || calendars.items().getFirst().id() == null) {
            throw unavailable("SCHEDULING_SERVICE_UNAVAILABLE");
        }
        return calendars.items().getFirst().id();
    }

    private SchedulingDecision postSchedulingValidation(UUID calendarId, BookingRequestFacts facts,
                                                        String timezone, String bearerToken) {
        try {
            ApiResponse<SchedulingDecision> response = schedulingClient.post()
                    .uri("/api/v1/scheduling-constraints/validate")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SchedulingValidationRequest(calendarId, facts.officeBuildingId(), facts.startsAt(),
                            facts.endsAt(), timezone, facts.recurrenceRuleId()))
                    .retrieve()
                    .body(SCHEDULING_RESPONSE);
            if (response == null || !response.success() || response.data() == null) {
                throw unavailable("SCHEDULING_SERVICE_UNAVAILABLE");
            }
            return response.data();
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw authenticationRequired("Scheduling");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw accessDenied("Scheduling");
        } catch (HttpClientErrorException.NotFound exception) {
            throw invalidReference("The working calendar or recurrence rule was not found");
        } catch (RestClientException exception) {
            throw unavailable("SCHEDULING_SERVICE_UNAVAILABLE");
        }
    }

    private static void validateRoomPolicy(BookingRequestFacts facts, RoomRuleReference rule, Instant now) {
        if (rule == null) {
            throw unavailable("ROOM_BOOKING_POLICY_UNAVAILABLE");
        }
        if (rule.releaseBufferMinutes() < 0) {
            throw unavailable("ROOM_BOOKING_POLICY_UNAVAILABLE");
        }
        if (facts.startsAt() == null || facts.endsAt() == null || !facts.endsAt().isAfter(facts.startsAt())) {
            throw invalidRequest();
        }
        if (facts.startsAt().isBefore(now)) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_START_IN_PAST",
                    "Requested start time must not be in the past");
        }
        Duration duration = Duration.between(facts.startsAt(), facts.endsAt());
        if (rule.minDurationMinutes() != null && duration.compareTo(Duration.ofMinutes(rule.minDurationMinutes())) < 0
                || rule.maxDurationMinutes() != null
                && duration.compareTo(Duration.ofMinutes(rule.maxDurationMinutes())) > 0) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "ROOM_DURATION_POLICY_VIOLATION",
                    "Requested duration is outside the active room policy");
        }
        Duration advance = Duration.between(now, facts.startsAt());
        if (rule.minAdvanceMinutes() != null && advance.compareTo(Duration.ofMinutes(rule.minAdvanceMinutes())) < 0
                || rule.maxAdvanceDays() != null && advance.compareTo(Duration.ofDays(rule.maxAdvanceDays())) > 0) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "ROOM_ADVANCE_POLICY_VIOLATION",
                    "Requested time is outside the active room booking window");
        }
        if (facts.recurrenceRuleId() != null && !rule.recurringAllowed()) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "ROOM_RECURRENCE_NOT_ALLOWED",
                    "The active room policy does not allow recurring bookings");
        }
        if (Boolean.TRUE.equals(facts.externalGuests()) && !rule.externalGuestsAllowed()) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "ROOM_EXTERNAL_GUESTS_NOT_ALLOWED",
                    "The active room policy does not allow external guests");
        }
        if (rule.allowedDepartmentIds() != null && !rule.allowedDepartmentIds().isEmpty()
                && !rule.allowedDepartmentIds().contains(facts.departmentId())) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ROOM_DEPARTMENT_ACCESS_DENIED",
                    "The active room policy does not allow the selected department");
        }
    }

    private static RoomRuleReference effectiveRule(List<RoomRuleReference> roomRules,
                                                   List<RoomRuleReference> buildingRules, Instant startsAt) {
        RoomRuleReference rule = latestActiveRule(roomRules, startsAt);
        return rule == null ? latestActiveRule(buildingRules, startsAt) : rule;
    }

    private static RoomRuleReference latestActiveRule(List<RoomRuleReference> rules, Instant startsAt) {
        if (rules == null) {
            throw unavailable("ROOM_BOOKING_POLICY_UNAVAILABLE");
        }
        List<RoomRuleReference> activeRules = rules.stream()
                .filter(RoomRuleReference::active)
                .filter(rule -> rule.effectiveFrom() == null || !rule.effectiveFrom().isAfter(startsAt))
                .toList();
        if (activeRules.isEmpty()) {
            return null;
        }
        Instant latest = activeRules.stream()
                .map(rule -> rule.effectiveFrom() == null ? Instant.MIN : rule.effectiveFrom())
                .max(Instant::compareTo)
                .orElseThrow();
        List<RoomRuleReference> latestRules = activeRules.stream()
                .filter(rule -> (rule.effectiveFrom() == null ? Instant.MIN : rule.effectiveFrom()).equals(latest))
                .toList();
        if (latestRules.size() != 1) {
            throw unavailable("ROOM_BOOKING_POLICY_UNAVAILABLE");
        }
        return latestRules.getFirst();
    }

    private static <T> ApiResponse<T> get(RestClient client, String path, UUID id, String bearerToken,
                                          ParameterizedTypeReference<ApiResponse<T>> responseType,
                                          String unavailableCode) {
        try {
            ApiResponse<T> response = client.get()
                    .uri(path, id)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(responseType);
            if (response == null || !response.success() || response.data() == null) {
                throw unavailable(unavailableCode);
            }
            return response;
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw authenticationRequired("Owner service");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw accessDenied("Owner service");
        } catch (HttpClientErrorException.NotFound exception) {
            throw invalidReference("An authoritative Organization or Room reference was not found");
        } catch (RestClientException exception) {
            throw unavailable(unavailableCode);
        }
    }

    private static void requireBearer(String bearerToken) {
        if (bearerToken == null || !bearerToken.regionMatches(true, 0, "Bearer ", 0, 7)
                || bearerToken.substring(7).isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Authentication is required to validate booking references");
        }
    }

    private static DomainException invalidRequest() {
        return new DomainException(HttpStatus.BAD_REQUEST, "BOOKING_REQUEST_INVALID",
                "Booking request facts are invalid");
    }

    private static DomainException invalidReference(String message) {
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_REFERENCE_INVALID", message);
    }

    private static DomainException authenticationRequired(String serviceName) {
        return new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                serviceName + " requires authentication");
    }

    private static DomainException accessDenied(String serviceName) {
        return new DomainException(HttpStatus.FORBIDDEN, "OWNER_SERVICE_ACCESS_DENIED",
                serviceName + " denied access to authoritative booking data");
    }

    private static DomainException unavailable(String code) {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, code,
                "An authoritative booking dependency is unavailable; the request cannot proceed");
    }

    private static RestClient.Builder timeoutBoundBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory);
    }

    public record BuildingReference(UUID id, boolean active, String timezone, UUID workingCalendarId) { }

    public record DepartmentReference(UUID id, UUID officeBuildingId, String status) { }

    public record RoomReference(UUID id, UUID officeBuildingId, int capacity, String roomClass,
                                String status, Instant deletedAt) { }

    public record RoomRuleReference(UUID id, UUID roomId, UUID officeBuildingId, Integer minDurationMinutes,
                                    Integer maxDurationMinutes, Integer minAdvanceMinutes, Integer maxAdvanceDays,
                                    Integer cancellationDeadlineMinutes, boolean recurringAllowed,
                                    boolean externalGuestsAllowed, boolean approvalRequired,
                                    boolean outsideHoursAllowed, int releaseBufferMinutes, boolean active,
                                    Instant effectiveFrom, List<UUID> allowedDepartmentIds) { }

    public record CalendarPage(List<CalendarReference> items) { }

    public record CalendarReference(UUID id) { }

    public record SchedulingValidationRequest(UUID workingCalendarId, UUID officeBuildingId,
                                              Instant startsAt, Instant endsAt, String timezone,
                                              UUID recurrenceRuleId) { }

    public record SchedulingDecision(UUID workingCalendarId, UUID recurrenceRuleId, String timezone,
                                     boolean valid, int occurrencesEvaluated,
                                     List<SchedulingViolation> violations,
                                     List<SchedulingOccurrence> occurrenceIntervals) { }

    public record SchedulingOccurrence(LocalDate occurrenceDate, Instant startsAt, Instant endsAt) { }

    public record SchedulingViolation(LocalDate occurrenceDate, String code, String message) { }

    public record MaintenancePeriodReference(UUID id, UUID roomId, String period, String reason,
                                             UUID createdByUserId) { }

    private record TimeRange(Instant startsAt, Instant endsAt) { }

    public record BookingRequestFacts(UUID departmentId, UUID roomId, UUID officeBuildingId,
                                      UUID recurrenceRuleId, Instant startsAt, Instant endsAt,
                                      int attendeeCount, Boolean externalGuests) { }

    public record ValidatedBookingReferences(UUID officeBuildingId, UUID departmentId, UUID roomId,
                                             boolean vipRoom, String timezone, boolean approvalRequired,
                                             int releaseBufferMinutes, List<SchedulingOccurrence> occurrences) { }
}
