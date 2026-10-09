package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AvailabilitySearchRequest(
        @NotNull UUID workingCalendarId,
        @NotNull UUID officeBuildingId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Positive Integer minimumCapacity,
        @Positive Integer minimumDurationMinutes,
        @Size(max = 25) List<@NotNull UUID> facilityTypeIds,
        UUID departmentId,
        UUID recurrenceRuleId) {
}
