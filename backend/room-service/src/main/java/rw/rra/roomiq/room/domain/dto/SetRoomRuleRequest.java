package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SetRoomRuleRequest(
        @Positive(message = "minDurationMinutes must be positive")
        Integer minDurationMinutes,

        @Positive(message = "maxDurationMinutes must be positive")
        Integer maxDurationMinutes,

        @PositiveOrZero(message = "minAdvanceMinutes cannot be negative")
        Integer minAdvanceMinutes,

        @PositiveOrZero(message = "maxAdvanceDays cannot be negative")
        Integer maxAdvanceDays,

        @PositiveOrZero(message = "cancellationDeadlineMinutes cannot be negative")
        Integer cancellationDeadlineMinutes,

        @NotNull(message = "recurringAllowed is required")
        Boolean recurringAllowed,

        @NotNull(message = "externalGuestsAllowed is required")
        Boolean externalGuestsAllowed,

        @NotNull(message = "approvalRequired is required")
        Boolean approvalRequired,

        @NotNull(message = "outsideHoursAllowed is required")
        Boolean outsideHoursAllowed,

        @Min(value = 0, message = "releaseBufferMinutes cannot be negative")
        Integer releaseBufferMinutes,

        @NotNull(message = "active is required")
        Boolean active,

        Instant effectiveFrom,

        @NotNull(message = "allowedDepartmentIds is required; use an empty array for unrestricted access")
        @Size(max = 100, message = "At most 100 allowed departments may be configured")
        List<@NotNull UUID> allowedDepartmentIds
) {
}