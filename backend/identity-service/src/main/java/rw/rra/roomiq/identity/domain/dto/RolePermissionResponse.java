package rw.rra.roomiq.identity.domain.dto;

import rw.rra.roomiq.identity.domain.entity.RolePermission;

import java.util.UUID;

public record RolePermissionResponse(UUID assignmentId, UUID roleId, UUID permissionId,
                                    String permissionCode, String permissionName) {
    public static RolePermissionResponse from(RolePermission assignment) {
        return new RolePermissionResponse(assignment.getId(), assignment.getRole().getId(),
                assignment.getPermission().getId(), assignment.getPermission().getCode(),
                assignment.getPermission().getName());
    }
}
