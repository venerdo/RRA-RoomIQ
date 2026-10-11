package rw.rra.roomiq.booking.domain.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBookingExtensionRequest(
        @NotNull Instant requestedEndAt) {
}
