package rw.rra.roomiq.identity.domain.dto;

import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String nationalId,
        String phone,
        String fullName,
        String displayName,
        UUID departmentId,
        UUID officeBuildingId,
        UserStatus status,
        Instant lastLoginAt,
        Integer version,
        Instant createdAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getNationalId(), user.getPhone(),
                user.getFullName(), user.getDisplayName(), user.getDepartmentId(),
                user.getOfficeBuildingId(), user.getStatus(), user.getLastLoginAt(),
                user.getVersion(), user.getCreatedAt());
    }
}
