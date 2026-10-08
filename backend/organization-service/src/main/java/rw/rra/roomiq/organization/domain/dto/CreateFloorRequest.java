package rw.rra.roomiq.organization.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFloorRequest(
        @NotNull UUID officeBuildingId,
        @NotBlank @Size(max = 120) String name,
        @NotNull Short level,
        Boolean active
) {
}