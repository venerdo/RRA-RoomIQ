package rw.rra.roomiq.scheduling;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import rw.rra.roomiq.scheduling.domain.entity.RecurrenceRule;
import rw.rra.roomiq.scheduling.domain.repository.RecurrenceRuleRepository;
import rw.rra.roomiq.scheduling.integration.SchedulingAuthorizationClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class RecurrenceRuleApiTests {
    private static final UUID ACTOR_ID = UUID.fromString("190e6630-2483-4d3f-a0fd-3a8c7b7f511d");
    private static final String API = "/api/v1/recurrence-rules";

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
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @MockitoBean
    private SchedulingAuthorizationClient authorizationClient;

    @BeforeEach
    void prepare() {
        recurrenceRuleRepository.deleteAll();
        when(authorizationClient.authorize(anyString(), anyString())).thenReturn(ACTOR_ID);
    }

    @Test
    void persistsRuleBoundsAndEvaluatesExactly365Occurrences() throws Exception {
        String created = create("FREQ=DAILY;COUNT=365", "2026-01-01", "Africa/Kigali")
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", "rrule-create"))
                .andExpect(jsonPath("$.data.occurrenceCount").value(365))
                .andExpect(jsonPath("$.data.endsOn").doesNotExist())
                .andExpect(jsonPath("$.data.createdByUserId").value(ACTOR_ID.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.data.id"));

        RecurrenceRule persisted = recurrenceRuleRepository.findById(id).orElseThrow();
        assertThat(persisted.getRrule()).isEqualTo("FREQ=DAILY;COUNT=365");
        assertThat(persisted.getStartsOn()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(persisted.getOccurrenceCount()).isEqualTo(365);
        assertThat(persisted.getCreatedByUserId()).isEqualTo(ACTOR_ID);

        mockMvc.perform(get(API + "/{id}/occurrences", id).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occurrences.length()").value(365))
                .andExpect(jsonPath("$.data.occurrences[0]").value("2026-01-01"))
                .andExpect(jsonPath("$.data.occurrences[364]").value("2026-12-31"))
                .andExpect(jsonPath("$.data.timezone").value("Africa/Kigali"));
    }

    @Test
    void rejectsMissingOrUnboundedEndConditionsAndCountsAboveTheLimit() throws Exception {
        assertInvalid("FREQ=DAILY", "2026-01-01", "Africa/Kigali", "RRULE_END_BOUND_REQUIRED");
        assertInvalid("FREQ=DAILY;COUNT=366", "2026-01-01", "Africa/Kigali",
                "RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED");
        assertInvalid("FREQ=YEARLY;COUNT=3", "2026-01-01", "Africa/Kigali",
                "RECURRENCE_HORIZON_EXCEEDED");
    }

    @Test
    void rejectsRulesOutsideTheOneYearHorizonAndInvalidUntilDates() throws Exception {
        assertInvalid("FREQ=WEEKLY;UNTIL=20280101", "2026-01-01", "Africa/Kigali",
                "RECURRENCE_HORIZON_EXCEEDED");
        assertInvalid("FREQ=DAILY;UNTIL=20260230", "2026-01-01", "Africa/Kigali",
                "INVALID_RRULE_UNTIL");
    }

    @Test
    void rejectsInvalidTimezoneAndStartDatePatternMismatch() throws Exception {
        assertInvalid("FREQ=DAILY;COUNT=2", "2026-01-01", "Mars/Olympus", "INVALID_TIMEZONE");
        assertInvalid("FREQ=WEEKLY;BYDAY=MO;COUNT=3", "2026-01-06", "Africa/Kigali",
                "RECURRENCE_START_DATE_MISMATCH");
        mockMvc.perform(post(API)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rrule\":\"FREQ=DAILY;COUNT=2\",\"startsOn\":\"2026-02-30\","
                                + "\"timezone\":\"Africa/Kigali\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void combinesUntilAndCountAndKeepsDateEvaluationStableAcrossDaylightSaving() throws Exception {
        String combined = create("FREQ=DAILY;COUNT=10;UNTIL=20260103", "2026-01-01", "Africa/Kigali")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endsOn").value("2026-01-03"))
                .andExpect(jsonPath("$.data.occurrenceCount").value(10))
                .andReturn().getResponse().getContentAsString();
        UUID combinedId = UUID.fromString(JsonPath.read(combined, "$.data.id"));
        mockMvc.perform(get(API + "/{id}/occurrences", combinedId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occurrences.length()").value(3));

        String dstRule = create("FREQ=DAILY;COUNT=4", "2026-03-07", "America/New_York")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID dstId = UUID.fromString(JsonPath.read(dstRule, "$.data.id"));
        String result = mockMvc.perform(get(API + "/{id}/occurrences", dstId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.data.occurrences.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        List<String> dates = JsonPath.read(result, "$.data.occurrences");
        assertThat(dates).containsExactly("2026-03-07", "2026-03-08", "2026-03-09", "2026-03-10");
    }

    @Test
    void evaluatesWeeklyMonthlyAndYearlyRulesDeterministically() throws Exception {
        String weeklyCreated = create("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=4",
                "2026-01-05", "Africa/Kigali")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID weeklyId = UUID.fromString(JsonPath.read(weeklyCreated, "$.data.id"));
        String weekly = mockMvc.perform(get(API + "/{id}/occurrences", weeklyId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(occurrenceDates(weekly))
                .containsExactly("2026-01-05", "2026-01-07", "2026-01-19", "2026-01-21");

        String monthlyCreated = create("FREQ=MONTHLY;BYMONTHDAY=-1;COUNT=3",
                "2026-01-31", "Africa/Kigali")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID monthlyId = UUID.fromString(JsonPath.read(monthlyCreated, "$.data.id"));
        String monthly = mockMvc.perform(get(API + "/{id}/occurrences", monthlyId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(occurrenceDates(monthly))
                .containsExactly("2026-01-31", "2026-02-28", "2026-03-31");

        String yearlyCreated = create("FREQ=YEARLY;BYMONTH=1,7;COUNT=3",
                "2026-01-01", "Africa/Kigali")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID yearlyId = UUID.fromString(JsonPath.read(yearlyCreated, "$.data.id"));
        String yearly = mockMvc.perform(get(API + "/{id}/occurrences", yearlyId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(occurrenceDates(yearly))
                .containsExactly("2026-01-01", "2026-07-01", "2027-01-01");
    }

    @Test
    void rejectsInvalidAndUnsupportedRRuleSyntax() throws Exception {
        assertInvalid("FREQ=FORTNIGHTLY;COUNT=2", "2026-01-01", "Africa/Kigali",
                "INVALID_RRULE_FREQUENCY");
        assertInvalid("FREQ=DAILY;COUNT=2;COUNT=3", "2026-01-01", "Africa/Kigali", "INVALID_RRULE");
        assertInvalid("FREQ=DAILY;COUNT=2;BYHOUR=9", "2026-01-01", "Africa/Kigali",
                "UNSUPPORTED_RRULE_COMPONENT");
    }

    @Test
    void supportsBoundedCrudListingAndOpenApiContract() throws Exception {
        String created = create("FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5", "2026-01-05", "Africa/Kigali")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.data.id"));

        mockMvc.perform(get(API).header("Authorization", "Bearer test-token").param("page", "0").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(API + "/{id}", id).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rrule").value("FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5"));
        mockMvc.perform(put(API + "/{id}", id).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("FREQ=DAILY;COUNT=2", "2026-01-05", "Africa/Kigali")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rrule").value("FREQ=DAILY;COUNT=2"))
                .andExpect(jsonPath("$.data.createdByUserId").value(ACTOR_ID.toString()));
        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/recurrence-rules'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/recurrence-rules/{recurrenceRuleId}/occurrences'].get").exists());
        mockMvc.perform(delete(API + "/{id}", id).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get(API + "/{id}", id).header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURRENCE_RULE_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions create(String rule, String startsOn, String timezone)
            throws Exception {
        return mockMvc.perform(post(API)
                .header("Authorization", "Bearer test-token")
                .header("X-Correlation-ID", "rrule-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(rule, startsOn, timezone)));
    }

    private void assertInvalid(String rule, String startsOn, String timezone, String code) throws Exception {
        create(rule, startsOn, timezone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    private static String request(String rule, String startsOn, String timezone) {
        return "{\"rrule\":\"" + rule + "\",\"startsOn\":\"" + startsOn
                + "\",\"timezone\":\"" + timezone + "\"}";
    }

    private List<String> occurrenceDates(String response) {
        List<?> dates = JsonPath.read(response, "$.data.occurrences");
        return dates.stream().map(String.class::cast).toList();
    }
}
