package rw.rra.roomiq.identity.domain.dto;

import rw.rra.roomiq.identity.domain.entity.Permission;

import java.util.UUID;

public record PermissionResponse(UUID id, String code, String name, String domain) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName(), permission.getDomain());
    }
}
