package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import rw.rra.roomiq.room.domain.entity.FacilityState;

import java.time.Instant;
import java.util.UUID;

public record SetRoomFacilityRequest(
        @NotNull(message = "facilityTypeId is required")
        UUID facilityTypeId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be positive")
        @Max(value = Short.MAX_VALUE, message = "quantity must fit the supported range")
        Integer quantity,

        @NotNull(message = "state is required")
        FacilityState state,

        Instant lastServicedAt
) {
}