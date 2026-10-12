package rw.rra.roomiq.booking.domain.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class BookingOccupancyRepository {
    private static final String FIND_OCCUPIED_INTERVALS = """
            SELECT room_id,
                   CASE WHEN lower_inf(occupied_period) THEN NULL ELSE lower(occupied_period) END AS starts_at,
                   CASE WHEN upper_inf(occupied_period) THEN NULL ELSE upper(occupied_period) END AS ends_at,
                   isempty(occupied_period) AS empty,
                   lower_inc(occupied_period) AS lower_inclusive,
                   upper_inc(occupied_period) AS upper_inclusive
            FROM reservation_occurrence
            WHERE room_id IN (:roomIds)
              AND occupancy_active = TRUE
              AND occupied_period && tstzrange(:startsAt, :endsAt, '[)')
            ORDER BY room_id, lower(occupied_period)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BookingOccupancyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<OccupancyInterval> findActiveIntervals(List<UUID> roomIds, Instant startsAt, Instant endsAt) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("roomIds", roomIds)
                .addValue("startsAt", Timestamp.from(startsAt))
                .addValue("endsAt", Timestamp.from(endsAt));
        return jdbcTemplate.query(FIND_OCCUPIED_INTERVALS, parameters, (resultSet, rowNumber) ->
                mapInterval(resultSet));
    }

    private static OccupancyInterval mapInterval(ResultSet resultSet) throws SQLException {
        OffsetDateTime startsAt = resultSet.getObject("starts_at", OffsetDateTime.class);
        OffsetDateTime endsAt = resultSet.getObject("ends_at", OffsetDateTime.class);
        return new OccupancyInterval(resultSet.getObject("room_id", UUID.class),
                startsAt == null ? null : startsAt.toInstant(),
                endsAt == null ? null : endsAt.toInstant(),
                resultSet.getBoolean("empty"),
                resultSet.getBoolean("lower_inclusive"),
                resultSet.getBoolean("upper_inclusive"));
    }

    public record OccupancyInterval(UUID roomId, Instant startsAt, Instant endsAt, boolean empty,
                                    boolean lowerInclusive, boolean upperInclusive) {
    }
}
