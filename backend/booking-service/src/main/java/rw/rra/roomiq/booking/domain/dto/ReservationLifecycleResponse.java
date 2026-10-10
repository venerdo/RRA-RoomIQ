package rw.rra.roomiq.booking.domain.dto;

import rw.rra.roomiq.booking.domain.enums.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationLifecycleResponse(
        UUID reservationId,
        ReservationStatus status,
        Instant checkedInAt,
        Instant completedAt) {
}
