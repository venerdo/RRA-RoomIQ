package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.room.domain.entity.RoomStatus;

public record ChangeRoomStatusRequest(
        @NotNull RoomStatus status,
        @NotBlank @Size(max = 2000) String reason
) {
}