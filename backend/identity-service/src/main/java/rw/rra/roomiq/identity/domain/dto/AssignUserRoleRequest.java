package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignUserRoleRequest(
        @NotNull UUID roleId,
        UUID scopeOfficeBuildingId
) {
}
