package rw.rra.roomiq.booking.domain.dto;

import rw.rra.roomiq.booking.domain.enums.BookingExtensionStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingExtensionResponse(
        UUID extensionId,
        UUID reservationId,
        UUID requestedByUserId,
        Instant previousEndAt,
        Instant requestedEndAt,
        Instant approvedEndAt,
        BookingExtensionStatus status,
        UUID decidedByUserId,
        String decisionComment,
        Instant requestedAt,
        Instant decidedAt) {
}
