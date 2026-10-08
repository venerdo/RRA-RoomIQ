package rw.rra.roomiq.scheduling;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
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

    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesSchedulingTablesAndPostgresIntegrityConstraints() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2', '3') AND success = TRUE",
                Integer.class)).isEqualTo(3);
        assertThat(tableNames()).contains("working_calendar", "working_day_window", "holiday",
                "closure_period", "recurrence_rule").doesNotContain("maintenance_period", "reservation", "booking_request");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname IN ('ck_working_day_window_day', 'ck_working_day_window_order',
                    'ck_closure_period_not_empty', 'ck_recurrence_rule_end_date',
                    'ck_recurrence_rule_occurrence_count')
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'uq_holiday_scope_date'
                """, Integer.class)).isEqualTo(1);
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
        workingDayWindowRepository.saveAndFlush(new WorkingDayWindow(
                calendar, (short) 1, LocalTime.of(9, 0), LocalTime.of(17, 0), true));
        LocalDate holidayDate = LocalDate.of(2026, 10, 7);
        holidayRepository.saveAndFlush(new Holiday(calendar, buildingId, holidayDate,
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
    void postgresRejectsInvalidWorkingWindowsRecurrenceBoundsEmptyClosuresAndDuplicateCalendars() {
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
