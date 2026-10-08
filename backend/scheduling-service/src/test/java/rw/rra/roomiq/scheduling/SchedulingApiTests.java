package rw.rra.roomiq.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;
import rw.rra.roomiq.scheduling.domain.repository.WorkingCalendarRepository;
import rw.rra.roomiq.scheduling.integration.SchedulingAuthorizationClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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
class SchedulingApiTests {
    private static final UUID ACTOR_ID = UUID.fromString("d7b3cf29-3d94-4d10-9e94-a47e62f57b61");

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
    private WorkingCalendarRepository calendars;

    @MockitoBean
    private SchedulingAuthorizationClient authorizationClient;

    @BeforeEach
    void authorizeSchedulingRequests() {
        when(authorizationClient.authorize(anyString(), anyString())).thenReturn(ACTOR_ID);
    }

    @Test
    void calendarAndWindowApisSupportCrudAndPreserveSharedEnvelope() throws Exception {
        UUID buildingId = UUID.randomUUID();
        String calendarBody = """
                {"name":"Calendar %s","officeBuildingId":"%s","timezone":"Africa/Kigali",
                 "defaultCalendar":true,"active":true}
                """.formatted(UUID.randomUUID(), buildingId);
        String createdCalendar = mockMvc.perform(post("/api/v1/working-calendars")
                        .header("Authorization", "Bearer scheduling-test")
                        .header("X-Correlation-ID", "calendar-create")
                        .contentType(APPLICATION_JSON).content(calendarBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", "calendar-create"))
                .andExpect(jsonPath("$.data.timezone").value("Africa/Kigali"))
                .andReturn().getResponse().getContentAsString();
        UUID calendarId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createdCalendar, "$.data.id"));

        String windowBody = "{\"dayOfWeek\":1,\"openTime\":\"09:00:00\",\"closeTime\":\"17:00:00\",\"workingDay\":true}";
        String createdWindow = mockMvc.perform(post("/api/v1/working-calendars/{calendarId}/working-day-windows", calendarId)
                        .header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON).content(windowBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dayOfWeek").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID windowId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createdWindow, "$.data.id"));

        mockMvc.perform(get("/api/v1/working-calendars/{calendarId}/working-day-windows", calendarId)
                        .header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(put("/api/v1/working-calendars/{calendarId}/working-day-windows/{windowId}", calendarId, windowId)
                        .header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"dayOfWeek\":2,\"openTime\":\"10:00:00\",\"closeTime\":\"16:00:00\",\"workingDay\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dayOfWeek").value(2));
        mockMvc.perform(delete("/api/v1/working-calendars/{calendarId}/working-day-windows/{windowId}", calendarId, windowId)
                        .header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/working-calendars").header("Authorization", "Bearer scheduling-test")
                        .param("officeBuildingId", buildingId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(get("/api/v1/working-calendars/{calendarId}", calendarId)
                        .header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(calendarId.toString()));
        mockMvc.perform(put("/api/v1/working-calendars/{calendarId}", calendarId)
                        .header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Updated calendar\",\"timezone\":\"Africa/Kigali\",\"defaultCalendar\":false,\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Updated calendar"));
        mockMvc.perform(delete("/api/v1/working-calendars/{calendarId}", calendarId)
                        .header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isOk());
    }

    @Test
    void calendarAndWindowRequestsRejectInvalidTimezoneWeekdayAndTimeOrder() throws Exception {
        mockMvc.perform(post("/api/v1/working-calendars")
                        .header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Invalid zone\",\"officeBuildingId\":null,\"timezone\":\"Mars/Olympus\",\"defaultCalendar\":false,\"active\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
        WorkingCalendar calendar = createCalendar();
        String path = "/api/v1/working-calendars/" + calendar.getId() + "/working-day-windows";
        mockMvc.perform(post(path).header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"dayOfWeek\":8,\"openTime\":\"09:00:00\",\"closeTime\":\"17:00:00\",\"workingDay\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(path).header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"openTime\":\"17:00:00\",\"closeTime\":\"09:00:00\",\"workingDay\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_WORKING_WINDOW"));
    }

    @Test
    void identityOutageAndMissingBearerFailClosed() throws Exception {
        mockMvc.perform(get("/api/v1/holidays"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        doThrow(new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AUTHORIZATION_UNAVAILABLE",
                "Identity authorization is unavailable"))
                .when(authorizationClient).authorize("Bearer scheduling-test", "READ");
        mockMvc.perform(get("/api/v1/holidays").header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_AUTHORIZATION_UNAVAILABLE"));
    }

    @Test
    void holidayApisManageScopeStateAndIdentityDerivedCreator() throws Exception {
        UUID buildingId = UUID.randomUUID();
        WorkingCalendar calendar = calendars.saveAndFlush(new WorkingCalendar(
                "Holiday calendar " + UUID.randomUUID(), buildingId, "Africa/Kigali", false, true));
        String body = ("{\"workingCalendarId\":\"%s\",\"officeBuildingId\":\"%s\",\"holidayDate\":\"2026-12-25\","
                + "\"name\":\"RRA holiday\",\"blocksBooking\":true,\"active\":true}")
                .formatted(calendar.getId(), buildingId);
        String created = mockMvc.perform(post("/api/v1/holidays")
                        .header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as(result.getResponse().getContentAsString()).isEqualTo(201))
                .andExpect(jsonPath("$.data.createdByUserId").value(ACTOR_ID.toString()))
                .andExpect(jsonPath("$.data.blocksBooking").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID holidayId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.data.id"));

        mockMvc.perform(post("/api/v1/holidays").header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLIDAY_SCOPE_DATE_CONFLICT"));
        mockMvc.perform(get("/api/v1/holidays").header("Authorization", "Bearer scheduling-test")
                        .param("workingCalendarId", calendar.getId().toString()).param("active", "true")
                        .param("blocksBooking", "true").param("fromDate", "2026-12-01").param("toDate", "2026-12-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(put("/api/v1/holidays/{id}", holidayId).header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"workingCalendarId\":\"%s\",\"officeBuildingId\":\"%s\",\"holidayDate\":\"2026-12-25\",\"name\":\"Updated holiday\",\"blocksBooking\":false,\"active\":false}"
                                .formatted(calendar.getId(), buildingId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.blocksBooking").value(false))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.createdByUserId").value(ACTOR_ID.toString()));
        mockMvc.perform(get("/api/v1/holidays/{id}", holidayId).header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Updated holiday"));
        mockMvc.perform(delete("/api/v1/holidays/{id}", holidayId).header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/holidays").header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-12-25\",\"name\":\"Nationwide holiday\",\"blocksBooking\":true,\"active\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.workingCalendarId").doesNotExist())
                .andExpect(jsonPath("$.data.officeBuildingId").doesNotExist());
    }

    @Test
    void holidayRejectsMismatchedCalendarBuildingAndReversedDateFilter() throws Exception {
        WorkingCalendar calendar = createCalendar();
        mockMvc.perform(post("/api/v1/holidays").header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"workingCalendarId\":\"%s\",\"officeBuildingId\":\"%s\",\"holidayDate\":\"2026-12-25\",\"name\":\"Mismatch\",\"blocksBooking\":true,\"active\":true}"
                                .formatted(calendar.getId(), UUID.randomUUID())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HOLIDAY_SCOPE_MISMATCH"));
        mockMvc.perform(get("/api/v1/holidays").header("Authorization", "Bearer scheduling-test")
                        .param("fromDate", "2026-12-31").param("toDate", "2026-12-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_HOLIDAY_DATE_RANGE"));
        mockMvc.perform(post("/api/v1/holidays").header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-12-25\",\"name\":\"Missing state\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void closurePeriodApisSupportNationwideBuildingScopesAndValidateFiniteRanges() throws Exception {
        String closure = "{\"startsAt\":\"2026-12-25T09:00:00Z\",\"endsAt\":\"2026-12-25T17:00:00Z\","
                + "\"reason\":\"Nationwide closure\",\"blocksBooking\":true}";
        String created = mockMvc.perform(post("/api/v1/closure-periods").header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON).content(closure))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.blocksBooking").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID closureId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.data.id"));
        mockMvc.perform(get("/api/v1/closure-periods").header("Authorization", "Bearer scheduling-test")
                        .param("nationwide", "true").param("blocksBooking", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(get("/api/v1/closure-periods/{id}", closureId).header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.period").value(
                        org.hamcrest.Matchers.containsString("2026-12-25")));
        mockMvc.perform(put("/api/v1/closure-periods/{id}", closureId).header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"officeBuildingId\":\"%s\",\"startsAt\":\"2026-12-25T10:00:00Z\",\"endsAt\":\"2026-12-25T11:00:00Z\",\"reason\":\"Building closure\",\"blocksBooking\":false}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.blocksBooking").value(false))
                .andExpect(jsonPath("$.data.officeBuildingId").isNotEmpty());
        mockMvc.perform(delete("/api/v1/closure-periods/{id}", closureId).header("Authorization", "Bearer scheduling-test"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/closure-periods").header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"startsAt\":\"2026-12-25T17:00:00Z\",\"endsAt\":\"2026-12-25T09:00:00Z\",\"blocksBooking\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CLOSURE_PERIOD"));
        mockMvc.perform(post("/api/v1/closure-periods").header("Authorization", "Bearer scheduling-test")
                        .contentType(APPLICATION_JSON)
                        .content("{\"startsAt\":\"2026-12-25T09:00:00Z\",\"endsAt\":\"2026-12-25T17:00:00Z\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void schedulingOpenApiDocumentsAllS5OperationsAsProtectedAndCorrelated() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> paths = com.jayway.jsonpath.JsonPath.read(document, "$.paths");
        assertThat(paths).hasSize(11);
        int operations = 0;
        for (Map.Entry<?, ?> path : paths.entrySet()) {
            String pathName = path.getKey().toString();
            if (pathName.startsWith("/api/v1/working-calendars") || pathName.startsWith("/api/v1/holidays")
                    || pathName.startsWith("/api/v1/closure-periods")
                    || pathName.startsWith("/api/v1/recurrence-rules")) {
                for (Map.Entry<?, ?> operation : ((Map<?, ?>) path.getValue()).entrySet()) {
                    operations++;
                    Map<?, ?> details = (Map<?, ?>) operation.getValue();
                    assertThat(String.valueOf(details.get("security"))).contains("bearerAuth");
                    assertThat(String.valueOf(details.get("parameters"))).contains("X-Correlation-ID");
                    assertThat(String.valueOf(details.get("responses"))).contains("ApiError");
                }
            }
        }
        assertThat(operations).isEqualTo(25);
    }

    private WorkingCalendar createCalendar() {
        return calendars.saveAndFlush(new WorkingCalendar("Test calendar " + UUID.randomUUID(), UUID.randomUUID(),
                "Africa/Kigali", false, true));
    }
}
