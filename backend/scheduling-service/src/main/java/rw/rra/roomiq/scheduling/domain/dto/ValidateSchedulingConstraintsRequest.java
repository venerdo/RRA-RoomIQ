package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ValidateSchedulingConstraintsRequest(
        @NotNull UUID workingCalendarId,
        @NotNull UUID officeBuildingId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank String timezone,
        UUID recurrenceRuleId) {
}
