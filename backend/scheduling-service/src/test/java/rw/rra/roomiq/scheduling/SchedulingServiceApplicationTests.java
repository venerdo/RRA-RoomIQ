package rw.rra.roomiq.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.postgresql.util.PGobject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
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
import rw.rra.roomiq.scheduling.integration.SchedulingAuthorizationClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class SchedulingServiceApplicationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private WorkingCalendarRepository workingCalendarRepository;

    @Autowired
    private WorkingDayWindowRepository workingDayWindowRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private ClosurePeriodRepository closurePeriodRepository;

    @Autowired
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchedulingAuthorizationClient authorizationClient;

    @Test
    void contextLoads() {
    }

        @Test
        void calendarAndWorkingWindowApisPersistValidatedResources() throws Exception {
        UUID buildingId = UUID.randomUUID();
        String calendarJson = """
            {"name":"API calendar %s","officeBuildingId":"%s","timezone":"Africa/Kigali",
             "defaultCalendar":true,"active":true}
            """.formatted(UUID.randomUUID(), buildingId);
        String createdCalendar = mockMvc.perform(post("/api/v1/working-calendars")
                .header("Authorization", "Bearer scheduling-test")
                .header("X-Correlation-ID", "scheduling-calendar-create")
                .contentType(APPLICATION_JSON).content(calendarJson))
            .andExpect(status().isCreated())
            .andExpect(header().string("X-Correlation-ID", "scheduling-calendar-create"))
            .andExpect(jsonPath("$.data.timezone").value("Africa/Kigali"))
            .andReturn().getResponse().getContentAsString();
        UUID calendarId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createdCalendar, "$.data.id"));
        verify(authorizationClient).authorize("Bearer scheduling-test", "MANAGE");

        String createdWindow = mockMvc.perform(post("/api/v1/working-calendars/{calendarId}/working-day-windows", calendarId)
                .header("Authorization", "Bearer scheduling-test")
                .contentType(APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"openTime\":\"09:00:00\",\"closeTime\":\"17:00:00\",\"workingDay\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.dayOfWeek").value(1))
            .andExpect(jsonPath("$.data.closeTime").value("17:00:00"))
            .andReturn().getResponse().getContentAsString();
        UUID windowId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createdWindow, "$.data.id"));
        mockMvc.perform(get("/api/v1/working-calendars/{calendarId}/working-day-windows", calendarId)
                .header("Authorization", "Bearer scheduling-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(put("/api/v1/working-calendars/{calendarId}/working-day-windows/{windowId}",
                calendarId, windowId)
                .header("Authorization", "Bearer scheduling-test")
                .contentType(APPLICATION_JSON)
                .content("{\"dayOfWeek\":2,\"openTime\":\"10:00:00\",\"closeTime\":\"16:00:00\",\"workingDay\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dayOfWeek").value(2));
        mockMvc.perform(delete("/api/v1/working-calendars/{calendarId}/working-day-windows/{windowId}",
                calendarId, windowId)
                .header("Authorization", "Bearer scheduling-test"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/working-calendars")
                .header("Authorization", "Bearer scheduling-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(get("/api/v1/working-calendars/{calendarId}", calendarId)
                .header("Authorization", "Bearer scheduling-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(calendarId.toString()));
        mockMvc.perform(put("/api/v1/working-calendars/{calendarId}", calendarId)
                .header("Authorization", "Bearer scheduling-test")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Updated API calendar\",\"timezone\":\"Africa/Kigali\",\"defaultCalendar\":false,\"active\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Updated API calendar"));
        mockMvc.perform(delete("/api/v1/working-calendars/{calendarId}", calendarId)
                .header("Authorization", "Bearer scheduling-test"))
            .andExpect(status().isOk());
        }

        @Test
        void calendarApisRejectInvalidTimezonesAndFailClosedOnIdentityOutage() throws Exception {
            mockMvc.perform(get("/api/v1/working-calendars"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/v1/working-calendars")
                .header("Authorization", "Bearer scheduling-test")
                .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Bad timezone\",\"timezone\":\"Mars/Olympus\",\"defaultCalendar\":false,\"active\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));

        doThrow(new rw.rra.roomiq.common.web.DomainException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "IDENTITY_AUTHORIZATION_UNAVAILABLE", "Identity authorization is unavailable"))
            .when(authorizationClient).authorize("Bearer scheduling-test", "READ");
        mockMvc.perform(get("/api/v1/working-calendars")
                .header("Authorization", "Bearer scheduling-test"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("IDENTITY_AUTHORIZATION_UNAVAILABLE"));
        }

        @Test
        void windowsRejectInvalidDaysAndTimeOrderAndCalendarDeletePreservesReferences() throws Exception {
        WorkingCalendar calendar = workingCalendarRepository.saveAndFlush(new WorkingCalendar(
            "API window test " + UUID.randomUUID(), UUID.randomUUID(), "Africa/Kigali", false, true));
        String path = "/api/v1/working-calendars/" + calendar.getId() + "/working-day-windows";
        mockMvc.perform(post(path).header("Authorization", "Bearer scheduling-test")
                .contentType(APPLICATION_JSON)
                        .content("{\"dayOfWeek\":8,\"openTime\":\"09:00:00\",\"closeTime\":\"17:00:00\",\"workingDay\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(path).header("Authorization", "Bearer scheduling-test")
                .contentType(APPLICATION_JSON)
                        .content("{\"dayOfWeek\":1,\"openTime\":\"17:00:00\",\"closeTime\":\"09:00:00\",\"workingDay\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_WORKING_WINDOW"));
        windowRepositorySave(calendar);
        mockMvc.perform(delete("/api/v1/working-calendars/{calendarId}", calendar.getId())
                .header("Authorization", "Bearer scheduling-test"))
            .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKING_CALENDAR_IN_USE"));
        }

        @Test
        void calendarOpenApiDocumentsAllOperationsWithBearerAndCorrelation() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Map<?, ?> paths = com.jayway.jsonpath.JsonPath.read(document, "$.paths");
            assertThat(paths).hasSize(4);
        int operations = 0;
            for (Map.Entry<?, ?> path : paths.entrySet()) {
                if (path.getKey().toString().startsWith("/api/v1/working-calendars")) {
                    for (Map.Entry<?, ?> operation : ((Map<?, ?>) path.getValue()).entrySet()) {
                operations++;
                        Map<?, ?> details = (Map<?, ?>) operation.getValue();
                        assertThat(String.valueOf(details.get("security"))).contains("bearerAuth");
                        assertThat(String.valueOf(details.get("parameters"))).contains("X-Correlation-ID");
                        assertThat(String.valueOf(details.get("responses"))).contains("ApiError");
            }
            }
        }
        assertThat(operations).isEqualTo(9);
            assertThat(paths.toString()).contains("/api/v1/working-calendars");
        }

        private void windowRepositorySave(WorkingCalendar calendar) {
        workingDayWindowRepository.saveAndFlush(new WorkingDayWindow(calendar, (short) 1,
            LocalTime.of(9, 0), LocalTime.of(17, 0), true));
        }

    @Test
    void flywayCreatesSchedulingTablesAndPostgresIntegrityConstraints() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2') AND success = TRUE", Integer.class))
        .isEqualTo(2);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE", Integer.class))
        .isEqualTo(1);
    assertThat(tableNames()).contains("working_calendar", "working_day_window", "holiday",
        "closure_period", "recurrence_rule");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM pg_constraint
        WHERE conname IN ('ck_working_day_window_day', 'ck_working_day_window_order',
            'ck_closure_period_not_empty', 'ck_recurrence_rule_end_date',
            'ck_recurrence_rule_occurrence_count')
        """, Integer.class)).isEqualTo(5);
    assertThat(jdbcTemplate.queryForObject("""
        SELECT udt_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'closure_period' AND column_name = 'period'
        """, String.class)).isEqualTo("tstzrange");
    }

    @Test
    void onlySchedulingOwnedRelationshipsHaveForeignKeys() throws SQLException {
    assertThat(importedForeignKeyColumns("working_calendar")).isEmpty();
    assertThat(importedForeignKeyColumns("working_day_window")).containsExactly("working_calendar_id");
    assertThat(importedForeignKeyColumns("holiday")).containsExactly("working_calendar_id");
    assertThat(importedForeignKeyColumns("closure_period")).isEmpty();
    assertThat(importedForeignKeyColumns("recurrence_rule")).isEmpty();
    }

    @Test
    @Transactional
    void schedulingEntitiesAndRepositoriesRoundTripAgainstMigratedSchema() throws Exception {
    assertThat(entityManagerFactory.getMetamodel().getEntities())
        .extracting(entityType -> entityType.getJavaType().getSimpleName())
        .containsExactlyInAnyOrder("WorkingCalendar", "WorkingDayWindow", "Holiday",
            "ClosurePeriod", "RecurrenceRule");

    UUID buildingId = UUID.randomUUID();
    UUID creatorId = UUID.randomUUID();
    WorkingCalendar calendar = workingCalendarRepository.saveAndFlush(
        new WorkingCalendar("RRA Kigali", buildingId, "Africa/Kigali", true, true));
    WorkingDayWindow window = workingDayWindowRepository.saveAndFlush(new WorkingDayWindow(
        calendar, (short) 1, LocalTime.of(9, 0), LocalTime.of(17, 0), true));
    LocalDate holidayDate = LocalDate.of(2026, 10, 7);
    Holiday holiday = holidayRepository.saveAndFlush(new Holiday(calendar, buildingId, holidayDate,
        "RRA event", true, true, creatorId));
    PGobject period = new PGobject();
    period.setType("tstzrange");
    period.setValue("[2026-10-07T09:00:00Z,2026-10-07T12:00:00Z)");
    ClosurePeriod closure = closurePeriodRepository.saveAndFlush(new ClosurePeriod(
        buildingId, period, "Building event", true));
    RecurrenceRule recurrence = recurrenceRuleRepository.saveAndFlush(new RecurrenceRule(
        "FREQ=WEEKLY;COUNT=4", LocalDate.of(2026, 10, 5), null, 4,
        "Africa/Kigali", creatorId));

    entityManager.clear();

    assertThat(workingCalendarRepository.findById(calendar.getId()).orElseThrow().getTimezone())
        .isEqualTo("Africa/Kigali");
    WorkingDayWindow reloadedWindow = workingDayWindowRepository.findAllByWorkingCalendar_IdOrderByDayOfWeek(
        calendar.getId()).getFirst();
    assertThat(reloadedWindow.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(reloadedWindow.getCloseTime()).isEqualTo(LocalTime.of(17, 0));
    assertThat(holidayRepository.findAllByHolidayDateAndActiveTrue(holidayDate))
        .extracting(Holiday::getCreatedByUserId).containsExactly(creatorId);
    assertThat(closurePeriodRepository.findById(closure.getId()).orElseThrow().getPeriod().getType())
        .isEqualTo("tstzrange");
    assertThat(jdbcTemplate.queryForObject("""
        SELECT lower(period) = TIMESTAMPTZ '2026-10-07T09:00:00Z'
            AND upper(period) = TIMESTAMPTZ '2026-10-07T12:00:00Z'
        FROM closure_period WHERE id = ?
        """, Boolean.class, closure.getId())).isTrue();
    RecurrenceRule reloadedRecurrence = recurrenceRuleRepository.findById(recurrence.getId()).orElseThrow();
    assertThat(reloadedRecurrence.getRrule()).isEqualTo("FREQ=WEEKLY;COUNT=4");
    assertThat(reloadedRecurrence.getCreatedAt()).isNotNull();
    }

    @Test
    void postgresRejectsInvalidWorkingWindowsRecurrenceBoundsAndEmptyClosures() {
    UUID calendarId = workingCalendarRepository.saveAndFlush(new WorkingCalendar(
        "Constraint test " + UUID.randomUUID(), null, "Africa/Kigali", false, true)).getId();

    assertThatThrownBy(() -> jdbcTemplate.update("""
        INSERT INTO working_day_window (id, working_calendar_id, day_of_week, open_time, close_time, is_working_day)
        VALUES (?, ?, 8, ?, ?, TRUE)
        """, UUID.randomUUID(), calendarId, Time.valueOf("09:00:00"), Time.valueOf("17:00:00")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbcTemplate.update("""
        INSERT INTO working_day_window (id, working_calendar_id, day_of_week, open_time, close_time, is_working_day)
        VALUES (?, ?, 1, ?, ?, TRUE)
        """, UUID.randomUUID(), calendarId, Time.valueOf("17:00:00"), Time.valueOf("09:00:00")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbcTemplate.update("""
        INSERT INTO recurrence_rule (id, rrule, starts_on, ends_on, occurrence_count, timezone, created_by_user_id)
        VALUES (?, 'FREQ=DAILY', DATE '2026-10-08', DATE '2026-10-07', 0, 'Africa/Kigali', ?)
        """, UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbcTemplate.update("""
        INSERT INTO closure_period (id, period, blocks_booking)
        VALUES (?, tstzrange(TIMESTAMPTZ '2026-10-07 09:00:00Z', TIMESTAMPTZ '2026-10-07 09:00:00Z', '[)'), TRUE)
        """, UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    UUID buildingId = UUID.randomUUID();
    workingCalendarRepository.saveAndFlush(new WorkingCalendar(
        "Building calendar " + UUID.randomUUID(), buildingId, "Africa/Kigali", true, true));
    assertThatThrownBy(() -> workingCalendarRepository.saveAndFlush(new WorkingCalendar(
        "Duplicate building calendar " + UUID.randomUUID(), buildingId, "Africa/Kigali", false, true)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    assertThat(workingCalendarRepository.saveAndFlush(new WorkingCalendar(
        "Global calendar one " + UUID.randomUUID(), null, "Africa/Kigali", false, true))).isNotNull();
    assertThat(workingCalendarRepository.saveAndFlush(new WorkingCalendar(
        "Global calendar two " + UUID.randomUUID(), null, "Africa/Kigali", false, true))).isNotNull();
    }

    private Set<String> tableNames() {
    return new HashSet<>(jdbcTemplate.query(
        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
        (resultSet, rowNumber) -> resultSet.getString(1)));
    }

    private Set<String> importedForeignKeyColumns(String tableName) throws SQLException {
    Set<String> columns = new HashSet<>();
    try (Connection connection = dataSource.getConnection();
         ResultSet keys = connection.getMetaData().getImportedKeys(connection.getCatalog(), "public", tableName)) {
        while (keys.next()) {
        columns.add(keys.getString("FKCOLUMN_NAME"));
        }
    }
    return columns;
    }
}
