package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record SetClosurePeriodRequest(
        UUID officeBuildingId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 2000) String reason,
        @NotNull Boolean blocksBooking) {
}