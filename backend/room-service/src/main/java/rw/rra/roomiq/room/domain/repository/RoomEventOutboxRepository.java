package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.room.domain.entity.RoomEventOutboxEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RoomEventOutboxRepository extends JpaRepository<RoomEventOutboxEntry, UUID> {
    @Query(value = """
            SELECT *
            FROM room_event_outbox
            WHERE published_at IS NULL
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RoomEventOutboxEntry> claimDue(@Param("now") Instant now);
}
