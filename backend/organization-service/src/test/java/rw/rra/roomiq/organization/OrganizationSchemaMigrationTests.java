package rw.rra.roomiq.organization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OrganizationSchemaMigrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayCreatesOrganizationTablesAndRequiredColumns() throws SQLException {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = TRUE",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = TRUE",
                Integer.class)).isEqualTo(1);

        assertThat(columns("country")).contains("id", "name", "iso_code", "is_active", "created_at", "updated_at");
        assertThat(columns("province")).contains("id", "country_id", "name", "is_active", "created_at");
        assertThat(columns("district")).contains("id", "province_id", "name", "is_active", "created_at");
        assertThat(columns("office_building")).contains("id", "district_id", "name", "code", "timezone",
                "working_calendar_id", "is_active", "created_at");
        assertThat(columns("floor")).contains("id", "office_building_id", "name", "level", "is_active", "created_at");
        assertThat(columns("department")).contains("id", "office_building_id", "parent_department_id", "name",
                "code", "status", "created_at");
        assertThat(columnSize("country", "name")).isEqualTo(150);
        assertThat(columnSize("office_building", "name")).isEqualTo(200);
        assertThat(columnSize("office_building", "code")).isEqualTo(64);
        assertThat(columnSize("office_building", "timezone")).isEqualTo(64);
        assertThat(columnSize("floor", "name")).isEqualTo(120);
        assertThat(columnSize("department", "code")).isEqualTo(64);
    }

    @Test
    void localHierarchyForeignKeysAndUniquenessAreEnforced() {
        UUID countryId = UUID.randomUUID();
        UUID provinceId = UUID.randomUUID();
        UUID districtId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        assertThatThrownBy(() -> insertProvince(UUID.randomUUID(), UUID.randomUUID(), "Missing parent"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO district (id, province_id, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "Missing province"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO office_building (id, district_id, name, code) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "Missing district", "MISSING-DISTRICT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO floor (id, office_building_id, name, level) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "Missing building", (short) 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("INSERT INTO country (id, name, iso_code) VALUES (?, ?, ?)",
                countryId, "Rwanda", "RW");
        insertProvince(provinceId, countryId, "Kigali City");
        jdbcTemplate.update("INSERT INTO district (id, province_id, name) VALUES (?, ?, ?)",
                districtId, provinceId, "Gasabo");
        jdbcTemplate.update("INSERT INTO office_building (id, district_id, name, code, working_calendar_id) "
                        + "VALUES (?, ?, ?, ?, ?)",
                buildingId, districtId, "RRA Headquarters", "HQ", UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO floor (id, office_building_id, name, level) VALUES (?, ?, ?, ?)",
                floorId, buildingId, "Ground Floor", (short) 0);
        jdbcTemplate.update("INSERT INTO department (id, office_building_id, name, code, status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                departmentId, buildingId, "Finance", "FIN", "ACTIVE");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO department (id, office_building_id, parent_department_id, name, code, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), buildingId, UUID.randomUUID(), "Missing Parent", "MISSING-PARENT", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID selfParentId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO department (id, office_building_id, parent_department_id, name, code, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                selfParentId, buildingId, selfParentId, "Self Parent", "SELF", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT created_at IS NOT NULL FROM floor WHERE id = ?",
                Boolean.class, floorId)).isTrue();
        assertThatThrownBy(() -> insertProvince(UUID.randomUUID(), countryId, "Kigali City"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBlankRequiredNamesAndCodes() {
        UUID countryId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO country (id, name) VALUES (?, ?)",
                UUID.randomUUID(), "   ")).isInstanceOf(DataIntegrityViolationException.class);
        jdbcTemplate.update("INSERT INTO country (id, name) VALUES (?, ?)", countryId, "Valid Country");

        assertThatThrownBy(() -> insertProvince(UUID.randomUUID(), countryId, " "))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID provinceId = UUID.randomUUID();
        insertProvince(provinceId, countryId, "Valid Province");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO district (id, province_id, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), provinceId, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID districtId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO district (id, province_id, name) VALUES (?, ?, ?)",
                districtId, provinceId, "Valid District");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO office_building (id, district_id, name, code) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), districtId, " ", "VALID-CODE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO office_building (id, district_id, name, code) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), districtId, "Valid Building", "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO office_building (id, district_id, name, code, timezone) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), districtId, "Valid Building", "VALID-CODE", " "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO office_building (id, district_id, name, code, timezone) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), districtId, "Malformed Timezone", "MALFORMED-TZ", "Africa Kigali"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID buildingId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO office_building (id, district_id, name, code) VALUES (?, ?, ?, ?)",
                buildingId, districtId, "Valid Building", "VALID-CODE");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO floor (id, office_building_id, name, level) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), buildingId, " ", (short) 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO department (id, office_building_id, name, code, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), buildingId, " ", "VALID", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO department (id, office_building_id, name, code, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), buildingId, "Valid Department", " ", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniquenessConstraintsFollowApprovedScopes() {
        UUID countryOne = UUID.randomUUID();
        UUID countryTwo = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO country (id, name, iso_code) VALUES (?, ?, ?)",
                countryOne, "Unique Country One", "U1");
        jdbcTemplate.update("INSERT INTO country (id, name, iso_code) VALUES (?, ?, ?)",
                countryTwo, "Unique Country Two", "U2");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO country (id, name, iso_code) VALUES (?, ?, ?)",
                UUID.randomUUID(), "Unique Country One", "U3"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO country (id, name, iso_code) VALUES (?, ?, ?)",
                UUID.randomUUID(), "Unique Country Three", "U1"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID provinceOne = UUID.randomUUID();
        UUID provinceTwo = UUID.randomUUID();
        insertProvince(provinceOne, countryOne, "Shared Province");
        insertProvince(provinceTwo, countryTwo, "Shared Province");
        assertThatThrownBy(() -> insertProvince(UUID.randomUUID(), countryOne, "Shared Province"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID districtOne = UUID.randomUUID();
        UUID districtTwo = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO district (id, province_id, name) VALUES (?, ?, ?)",
                districtOne, provinceOne, "Shared District");
        jdbcTemplate.update("INSERT INTO district (id, province_id, name) VALUES (?, ?, ?)",
                districtTwo, provinceTwo, "Shared District");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO district (id, province_id, name) VALUES (?, ?, ?)",
                UUID.randomUUID(), provinceOne, "Shared District"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID buildingOne = UUID.randomUUID();
        UUID buildingTwo = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO office_building (id, district_id, name, code) VALUES (?, ?, ?, ?)",
                buildingOne, districtOne, "Building One", "GLOBAL-BUILDING-CODE");
        jdbcTemplate.update("INSERT INTO office_building (id, district_id, name, code) VALUES (?, ?, ?, ?)",
                buildingTwo, districtTwo, "Building Two", "OTHER-BUILDING-CODE");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO office_building (id, district_id, name, code) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), districtTwo, "Building Three", "GLOBAL-BUILDING-CODE"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("INSERT INTO floor (id, office_building_id, name, level) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), buildingOne, "Shared Floor", (short) 0);
        jdbcTemplate.update("INSERT INTO floor (id, office_building_id, name, level) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), buildingTwo, "Shared Floor", (short) 0);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO floor (id, office_building_id, name, level) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), buildingOne, "Shared Floor", (short) 1))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("INSERT INTO department (id, office_building_id, name, code, status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), buildingOne, "Department One", "GLOBAL-DEPARTMENT-CODE", "ACTIVE");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO department (id, office_building_id, name, code, status) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), buildingTwo, "Department Two", "GLOBAL-DEPARTMENT-CODE", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Set<String> columns(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

        private int columnSize(String tableName, String columnName) throws SQLException {
                try (Connection connection = dataSource.getConnection();
                         ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
                        if (!resultSet.next()) {
                                throw new AssertionError("Column not found: " + tableName + "." + columnName);
                        }
                        return resultSet.getInt("COLUMN_SIZE");
                }
        }

    private void insertProvince(UUID id, UUID countryId, String name) {
        jdbcTemplate.update("INSERT INTO province (id, country_id, name) VALUES (?, ?, ?)", id, countryId, name);
    }
}