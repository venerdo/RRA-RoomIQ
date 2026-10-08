package rw.rra.roomiq.identity.domain.dto;

import rw.rra.roomiq.identity.domain.entity.Role;

import java.util.UUID;

public record RoleResponse(UUID id, String code, String name, String description, boolean system) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem());
    }
}
