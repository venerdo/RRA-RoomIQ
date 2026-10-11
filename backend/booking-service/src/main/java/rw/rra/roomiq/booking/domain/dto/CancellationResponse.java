package rw.rra.roomiq.booking.domain.dto;

import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.CancellationReason;
import rw.rra.roomiq.booking.domain.enums.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record CancellationResponse(
        UUID cancellationId,
        UUID reservationId,
        UUID bookingRequestId,
        UUID cancelledByUserId,
        CancellationReason reasonCode,
        String reason,
        Instant cancellationDeadlineAt,
        boolean overrideApplied,
        String overrideReason,
        Instant cancelledAt,
        ReservationStatus reservationStatus,
        BookingRequestStatus bookingRequestStatus) {
}
