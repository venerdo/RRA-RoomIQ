package rw.rra.roomiq.organization.domain.dto;

import jakarta.validation.constraints.NotNull;
import rw.rra.roomiq.organization.domain.entity.DepartmentStatus;

public record SetDepartmentStatusRequest(@NotNull DepartmentStatus status) {
}