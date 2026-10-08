package rw.rra.roomiq.organization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrganizationServiceApplicationTests {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void testContextUsesIsolatedH2AndFlywayValidatedSchema() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            assertThat(jdbcUrl).isEqualTo("jdbc:h2:mem:organization");
        }
        assertThat(environment.getProperty("spring.datasource.url"))
            .isEqualTo("jdbc:h2:mem:organization;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version IN ('1', '2', '3', '4') AND success = TRUE", Integer.class)).isEqualTo(4);
    }
}
