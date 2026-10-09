package rw.rra.roomiq.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.domain.entity.ClosurePeriod;
import rw.rra.roomiq.scheduling.domain.entity.Holiday;
import rw.rra.roomiq.scheduling.domain.entity.RecurrenceRule;
import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;
import rw.rra.roomiq.scheduling.domain.entity.WorkingDayWindow;
import rw.rra.roomiq.scheduling.domain.repository.ClosurePeriodRepository;
import rw.rra.roomiq.scheduling.domain.repository.HolidayRepository;
import rw.rra.roomiq.scheduling.domain.repository.RecurrenceRuleRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingCalendarRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingDayWindowRepository;
import rw.rra.roomiq.scheduling.integration.BookingOccupancyClient;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient;
import rw.rra.roomiq.scheduling.integration.SchedulingAuthorizationClient;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class SchedulingConstraintApiTests {
    private static final UUID ACTOR_ID = UUID.fromString("82f0d8ee-1cb0-461d-8c05-7e63df22cfeb");
    private static final UUID BUILDING_ID = UUID.fromString("6c15e462-c760-4b8e-8df7-503e8ee3e531");
    private static final String API = "/api/v1/scheduling-constraints/validate";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkingCalendarRepository calendarRepository;

    @Autowired
    private WorkingDayWindowRepository windowRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private ClosurePeriodRepository closureRepository;

    @Autowired
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @MockitoBean
    private SchedulingAuthorizationClient authorizationClient;

    @MockitoBean
    private RoomAvailabilityClient roomAvailabilityClient;

    @MockitoBean
    private BookingOccupancyClient bookingOccupancyClient;

    @BeforeEach
    void cleanState() {
        holidayRepository.deleteAll();
        closureRepository.deleteAll();
        windowRepository.deleteAll();
        recurrenceRuleRepository.deleteAll();
        calendarRepository.deleteAll();
        when(authorizationClient.authorize(anyString(), anyString())).thenReturn(ACTOR_ID);
    }

    @Test
    void acceptsRequestInsideWorkingWindowWithCalendarTimezone() throws Exception {
        WorkingCalendar calendar = calendar("Africa/Kigali", BUILDING_ID, true);
        window(calendar, 1, "09:00", "17:00", true);

        validate(calendar, "2026-01-05T10:00:00+02:00", "2026-01-05T11:00:00+02:00", "Africa/Kigali", null)
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "constraint-check"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.occurrencesEvaluated").value(1))
                .andExpect(jsonPath("$.data.occurrenceIntervals[0].occurrenceDate").value("2026-01-05"))
                .andExpect(jsonPath("$.data.occurrenceIntervals[0].startsAt").value("2026-01-05T08:00:00Z"))
                .andExpect(jsonPath("$.data.occurrenceIntervals[0].endsAt").value("2026-01-05T09:00:00Z"))
                .andExpect(jsonPath("$.data.violations").isEmpty());

        verify(authorizationClient).authorize("Bearer test-token", "READ");
    }

    @Test
    void rejectsOutsideHoursNonworkingDaysAndInactiveCalendars() throws Exception {
        WorkingCalendar calendar = calendar("Africa/Kigali", BUILDING_ID, true);
        window(calendar, 1, "09:00", "17:00", true);
        window(calendar, 2, "09:00", "17:00", false);

        validate(calendar, "2026-01-05T08:00:00+02:00", "2026-01-05T09:00:00+02:00", "Africa/Kigali", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.violations[0].code").value("OUTSIDE_WORKING_WINDOW"));
        validate(calendar, "2026-01-06T10:00:00+02:00", "2026-01-06T11:00:00+02:00", "Africa/Kigali", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.violations[0].code").value("OUTSIDE_WORKING_WINDOW"));

        WorkingCalendar inactive = calendar("Africa/Kigali", UUID.randomUUID(), false);
        window(inactive, 1, "09:00", "17:00", true);
        validate(inactive, "2026-01-05T10:00:00+02:00", "2026-01-05T11:00:00+02:00",
                "Africa/Kigali", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.violations[0].code").value("WORKING_CALENDAR_INACTIVE"));
    }

    @Test
    void appliesOnlyActiveBlockingHolidaysMatchingCalendarAndBuildingScope() throws Exception {
        WorkingCalendar calendar = calendar("Africa/Kigali", BUILDING_ID, true);
        window(calendar, 1, "09:00", "17:00", true);
        window(calendar, 2, "09:00", "17:00", true);
        window(calendar, 3, "09:00", "17:00", true);
        LocalDate date = LocalDate.parse("2026-01-05");
        holiday(calendar, BUILDING_ID, date, true, true);
        holiday(null, UUID.randomUUID(), date, true, true);
        holiday(null, null, date.plusDays(1), false, true);
        holiday(null, null, date.plusDays(2), true, false);

        validate(calendar, "2026-01-05T10:00:00+02:00", "2026-01-05T11:00:00+02:00",
                "Africa/Kigali", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.violations.length()").value(1))
                .andExpect(jsonPath("$.data.violations[0].code").value("BLOCKING_HOLIDAY"));
        validate(calendar, "2026-01-06T10:00:00+02:00", "2026-01-06T11:00:00+02:00",
                "Africa/Kigali", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));
        validate(calendar, "2026-01-07T10:00:00+02:00", "2026-01-07T11:00:00+02:00",
                "Africa/Kigali", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    void detectsBuildingAndNationwideClosureOverlapWithHalfOpenBoundaries() throws Exception {
        WorkingCalendar calendar = calendar("Africa/Kigali", BUILDING_ID, true);
        window(calendar, 1, "09:00", "17:00", true);
        closure(BUILDING_ID, "2026-01-05T08:30:00Z", "2026-01-05T08:45:00Z");

        validate(calendar, "2026-01-05T10:00:00+02:00", "2026-01-05T11:00:00+02:00",
                "Africa/Kigali", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.violations[0].code").value("BLOCKING_CLOSURE"));

        closureRepository.deleteAll();
        closure(null, "2026-01-05T07:00:00Z", "2026-01-05T08:00:00Z");
        validate(calendar, "2026-01-05T10:00:00+02:00", "2026-01-05T11:00:00+02:00",
                "Africa/Kigali", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.violations").isEmpty());
    }

    @Test
    void validatesEveryBoundedRecurrenceDateAndReusesLocalTimes() throws Exception {
        WorkingCalendar calendar = calendar("Africa/Kigali", BUILDING_ID, true);
        for (short day = 1; day <= 7; day++) {
            window(calendar, day, "09:00", "17:00", true);
        }
        RecurrenceRule rule = recurrenceRuleRepository.saveAndFlush(new RecurrenceRule(
                "FREQ=DAILY;COUNT=3", LocalDate.parse("2026-01-05"), null, 3,
                "Africa/Kigali", ACTOR_ID));
        holiday(null, null, LocalDate.parse("2026-01-06"), true, true);

        validate(calendar, "2026-01-05T10:00:00+02:00", "2026-01-05T11:00:00+02:00",
                "Africa/Kigali", rule.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.occurrencesEvaluated").value(3))
                .andExpect(jsonPath("$.data.occurrenceIntervals.length()").value(3))
                .andExpect(jsonPath("$.data.occurrenceIntervals[1].occurrenceDate").value("2026-01-06"))
                .andExpect(jsonPath("$.data.occurrenceIntervals[1].startsAt").value("2026-01-06T08:00:00Z"))
                .andExpect(jsonPath("$.data.occurrenceIntervals[2].endsAt").value("2026-01-07T09:00:00Z"))
                .andExpect(jsonPath("$.data.violations.length()").value(1))
                .andExpect(jsonPath("$.data.violations[0].occurrenceDate").value("2026-01-06"))
                .andExpect(jsonPath("$.data.violations[0].code").value("BLOCKING_HOLIDAY"));
    }

    @Test
    void rejectsRecurringLocalTimesThatDoNotExistAfterADaylightSavingTransition() throws Exception {
        WorkingCalendar calendar = calendar("America/New_York", BUILDING_ID, true);
        window(calendar, 6, "02:00", "04:00", true);
        window(calendar, 7, "02:00", "04:00", true);
        RecurrenceRule rule = recurrenceRuleRepository.saveAndFlush(new RecurrenceRule(
                "FREQ=DAILY;COUNT=2", LocalDate.parse("2026-03-07"), null, 2,
                "America/New_York", ACTOR_ID));

        validate(calendar, "2026-03-07T02:30:00-05:00", "2026-03-07T03:00:00-05:00",
                "America/New_York", rule.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.occurrencesEvaluated").value(2))
                .andExpect(jsonPath("$.data.occurrenceIntervals.length()").value(1))
                .andExpect(jsonPath("$.data.violations[0].occurrenceDate").value("2026-03-08"))
                .andExpect(jsonPath("$.data.violations[0].code").value("NONEXISTENT_LOCAL_TIME"));
    }

    @Test
    void rejectsInvalidTimezoneDateRangesAndMissingDependencies() throws Exception {
        WorkingCalendar calendar = calendar("Africa/Kigali", BUILDING_ID, true);
        window(calendar, 1, "09:00", "17:00", true);

        validate(calendar, "2026-01-05T10:00:00+02:00", "2026-01-06T11:00:00+02:00",
                "Africa/Kigali", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULING_INTERVAL"));
        validate(calendar, "2026-01-05T10:00:00Z", "2026-01-05T11:00:00Z", "UTC", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULING_TIMEZONE_MISMATCH"));

        mockMvc.perform(post(API).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workingCalendarId":"%s","officeBuildingId":"%s",
                                 "startsAt":"2026-01-05T10:00:00+02:00","endsAt":"2026-01-05T11:00:00+02:00",
                                 "timezone":"Africa/Kigali"}
                                """.formatted(UUID.randomUUID(), BUILDING_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKING_CALENDAR_NOT_FOUND"));

        RecurrenceRule missingRule = new RecurrenceRule("FREQ=DAILY;COUNT=2", LocalDate.parse("2026-01-05"),
                null, 2, "Africa/Kigali", ACTOR_ID);
        recurrenceRuleRepository.saveAndFlush(missingRule);
        validate(calendar, "2026-01-05T10:00:00+02:00", "2026-01-05T11:00:00+02:00",
                "Africa/Kigali", UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURRENCE_RULE_NOT_FOUND"));
    }

    @Test
    void failsClosedWhenIdentityAuthorizationIsUnavailable() throws Exception {
        WorkingCalendar calendar = calendar("Africa/Kigali", BUILDING_ID, true);
        when(authorizationClient.authorize(anyString(), eq("READ")))
                .thenThrow(new DomainException(HttpStatus.SERVICE_UNAVAILABLE,
                        "IDENTITY_AUTHORIZATION_UNAVAILABLE", "Identity authorization is unavailable"));

        validate(calendar, "2026-01-05T10:00:00+02:00", "2026-01-05T11:00:00+02:00",
                "Africa/Kigali", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_AUTHORIZATION_UNAVAILABLE"));
    }

    @Test
    void availabilitySearchUsesReadAuthorizationAndDoesNotClaimOccupancyForEmptyCandidates() throws Exception {
        WorkingCalendar calendar = calendar("Africa/Kigali", BUILDING_ID, true);
        window(calendar, 1, "09:00", "17:00", true);
        when(roomAvailabilityClient.rooms(BUILDING_ID)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/availability/search")
                        .header("Authorization", "Bearer test-token")
                        .header("X-Correlation-ID", "availability-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workingCalendarId":"%s","officeBuildingId":"%s",
                                 "startsAt":"2026-01-05T10:00:00+02:00",
                                 "endsAt":"2026-01-05T11:00:00+02:00"}
                                """.formatted(calendar.getId(), BUILDING_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "availability-check"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.timezone").value("Africa/Kigali"))
                .andExpect(jsonPath("$.data.bookingOccupancyIncluded").value(false))
                .andExpect(jsonPath("$.data.bookingConfirmationRequired").value(true))
                .andExpect(jsonPath("$.data.windows").isEmpty());

        verify(authorizationClient).authorize("Bearer test-token", "READ");
        verify(roomAvailabilityClient).rooms(BUILDING_ID);
        verifyNoInteractions(bookingOccupancyClient);
    }

    @Test
    void findsBlockingClosureIntervalsAndPreservesHalfOpenBoundaries() throws Exception {
        closure(BUILDING_ID, "2026-01-05T10:00:00Z", "2026-01-05T11:00:00Z");

        var overlapping = closureRepository.findBlockingOverlaps(BUILDING_ID,
                Instant.parse("2026-01-05T10:30:00Z"), Instant.parse("2026-01-05T11:30:00Z"));
        var adjacent = closureRepository.findBlockingOverlaps(BUILDING_ID,
                Instant.parse("2026-01-05T11:00:00Z"), Instant.parse("2026-01-05T12:00:00Z"));

        assertThat(overlapping).singleElement().satisfies(interval -> {
            assertThat(interval.getStartsAt()).isEqualTo(Instant.parse("2026-01-05T10:00:00Z"));
            assertThat(interval.getEndsAt()).isEqualTo(Instant.parse("2026-01-05T11:00:00Z"));
        });
        assertThat(adjacent).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions validate(
            WorkingCalendar calendar, String startsAt, String endsAt, String timezone, UUID recurrenceRuleId)
            throws Exception {
        String recurrenceField = recurrenceRuleId == null ? "" : ",\"recurrenceRuleId\":\"" + recurrenceRuleId + "\"";
        String body = "{\"workingCalendarId\":\"" + calendar.getId()
                + "\",\"officeBuildingId\":\"" + BUILDING_ID + "\",\"startsAt\":\"" + startsAt
                + "\",\"endsAt\":\"" + endsAt + "\",\"timezone\":\"" + timezone + "\"" + recurrenceField + "}";
        return mockMvc.perform(post(API)
                .header("Authorization", "Bearer test-token")
                .header("X-Correlation-ID", "constraint-check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private WorkingCalendar calendar(String timezone, UUID buildingId, boolean active) {
        return calendarRepository.saveAndFlush(new WorkingCalendar(
                "Constraint calendar " + UUID.randomUUID(), buildingId, timezone, false, active));
    }

    private void window(WorkingCalendar calendar, int day, String opens, String closes, boolean working) {
        windowRepository.saveAndFlush(new WorkingDayWindow(calendar, (short) day,
                LocalTime.parse(opens), LocalTime.parse(closes), working));
    }

    private void holiday(WorkingCalendar calendar, UUID buildingId, LocalDate date,
                         boolean blocksBooking, boolean active) {
        holidayRepository.saveAndFlush(new Holiday(calendar, buildingId, date, "Test holiday",
                blocksBooking, active, ACTOR_ID));
    }

    private void closure(UUID buildingId, String startsAt, String endsAt) throws SQLException {
        PGobject period = new PGobject();
        period.setType("tstzrange");
        period.setValue("[" + Instant.parse(startsAt) + "," + Instant.parse(endsAt) + ")");
        closureRepository.saveAndFlush(new ClosurePeriod(buildingId, period, "Test closure", true));
    }
}
