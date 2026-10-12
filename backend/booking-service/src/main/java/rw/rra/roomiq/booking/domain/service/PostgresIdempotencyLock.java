package rw.rra.roomiq.booking.domain.service;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.Objects;

@Component
public class PostgresIdempotencyLock {
    private final JdbcTemplate jdbcTemplate;

    public PostgresIdempotencyLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire(String operationScope, String keyScope) {
        Objects.requireNonNull(operationScope);
        Objects.requireNonNull(keyScope);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, operationScope + ":" + keyScope);
                statement.execute();
            }
            return null;
        });
    }
}
