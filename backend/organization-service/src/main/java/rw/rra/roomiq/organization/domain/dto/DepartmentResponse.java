package rw.rra.roomiq.organization.domain.dto;

import rw.rra.roomiq.organization.domain.entity.DepartmentStatus;

import java.time.Instant;
import java.util.UUID;

public record DepartmentResponse(UUID id, UUID officeBuildingId, UUID parentDepartmentId,
                                 String name, String code, DepartmentStatus status,
                                 Instant createdAt) {
}