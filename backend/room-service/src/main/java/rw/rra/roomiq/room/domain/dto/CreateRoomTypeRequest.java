package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoomTypeRequest(
        @NotBlank(message = "Room type code is required")
        @Size(max = 64, message = "Room type code must be at most 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Room type code may only contain letters, numbers, underscores, and hyphens")
        String code,

        @NotBlank(message = "Room type name is required")
        @Size(max = 120, message = "Room type name must be at most 120 characters")
        String name,

        Boolean active
) {
    public boolean activeFlag() {
        return active == null || active;
    }
}
