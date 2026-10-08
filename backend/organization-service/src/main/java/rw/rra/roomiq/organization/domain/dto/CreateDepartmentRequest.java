package rw.rra.roomiq.organization.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.organization.domain.entity.DepartmentStatus;

import java.util.UUID;

public record CreateDepartmentRequest(
        UUID officeBuildingId,
        UUID parentDepartmentId,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 64) String code,
        @NotNull DepartmentStatus status
) {
}