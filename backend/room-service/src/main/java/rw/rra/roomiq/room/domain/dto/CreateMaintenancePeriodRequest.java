package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateMaintenancePeriodRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 2000) String reason
) {
}