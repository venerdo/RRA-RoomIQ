package rw.rra.roomiq.booking.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingOccupancyRequest(
        @NotEmpty @Size(max = 5000) List<@NotNull UUID> roomIds,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull String contractVersion) {
}
