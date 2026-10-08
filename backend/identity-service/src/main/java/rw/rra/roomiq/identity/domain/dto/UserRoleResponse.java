package rw.rra.roomiq.identity.domain.dto;

import rw.rra.roomiq.identity.domain.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserRoleResponse(
        UUID assignmentId,
        UUID userId,
        UUID roleId,
        String roleCode,
        UUID scopeOfficeBuildingId,
        UUID grantedByUserId,
        Instant grantedAt
) {
    public static UserRoleResponse from(UserRole assignment) {
        return new UserRoleResponse(assignment.getId(), assignment.getUser().getId(),
                assignment.getRole().getId(), assignment.getRole().getCode(),
                assignment.getScopeOfficeBuildingId(),
                assignment.getGrantedBy() == null ? null : assignment.getGrantedBy().getId(),
                assignment.getGrantedAt());
    }
}
