package rw.rra.roomiq.booking.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record BookingOccupancyChangedEvent(
        UUID eventId,
        int eventVersion,
        UUID reservationId,
        UUID roomId,
        Action action,
        int reservationVersion,
        Instant occurredAt,
        OccupiedInterval occupiedInterval) {
    public enum Action {
        UPSERT,
        REMOVE
    }

    public record OccupiedInterval(Instant startsAt, Instant endsAt) {
    }
}
