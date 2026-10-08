package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRoomPhotoRequest(
        @Min(value = 0, message = "sortOrder cannot be negative")
        @Max(value = Short.MAX_VALUE, message = "sortOrder exceeds the supported range")
        Integer sortOrder,

        @NotNull(message = "primary is required")
        Boolean primary,

        @NotNull(message = "approvedForPublic is required")
        Boolean approvedForPublic
) {
}