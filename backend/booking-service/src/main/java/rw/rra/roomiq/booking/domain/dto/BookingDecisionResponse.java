package rw.rra.roomiq.booking.domain.dto;

import rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingDecisionResponse(
        UUID bookingRequestId,
        BookingRequestStatus requestStatus,
        ApprovalDecisionType decision,
        UUID decidedByUserId,
        Instant decidedAt,
        String comment,
        UUID reservationId,
        UUID meetingId) {
}
