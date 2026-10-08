package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFacilityTypeRequest(
        @NotBlank(message = "Facility type code is required")
        @Size(max = 64, message = "Facility type code must be at most 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Facility type code may only contain letters, numbers, underscores, and hyphens")
        String code,

        @NotBlank(message = "Facility type name is required")
        @Size(max = 120, message = "Facility type name must be at most 120 characters")
        String name,

        @Size(max = 120, message = "Facility category must be at most 120 characters")
        String category,

        Boolean active
) {
    public boolean activeFlag() {
        return active == null || active;
    }
}
