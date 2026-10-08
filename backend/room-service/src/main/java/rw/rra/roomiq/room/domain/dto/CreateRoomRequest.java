package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.room.domain.entity.RoomClass;
import rw.rra.roomiq.room.domain.entity.RoomStatus;

import java.util.UUID;

public record CreateRoomRequest(
        UUID roomTypeId,
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Room type code may only contain letters, numbers, underscores, and hyphens")
        String roomTypeCode,

        @NotNull(message = "floorId is required")
        UUID floorId,

        @NotNull(message = "officeBuildingId is required")
        UUID officeBuildingId,

        @NotBlank(message = "Room name is required")
        @Size(max = 150, message = "Room name must be at most 150 characters")
        String name,

        @NotBlank(message = "Room code is required")
        @Size(max = 64, message = "Room code must be at most 64 characters")
        String code,

        @Size(max = 2000, message = "Room description must be at most 2000 characters")
        String description,

        @NotNull(message = "capacity is required")
        @Min(value = 1, message = "capacity must be positive")
        Integer capacity,

        @NotNull(message = "roomClass is required")
        RoomClass roomClass,

        @NotNull(message = "status is required")
        RoomStatus status
) {
}
