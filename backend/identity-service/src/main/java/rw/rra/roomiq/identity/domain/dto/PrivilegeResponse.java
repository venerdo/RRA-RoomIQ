package rw.rra.roomiq.identity.domain.dto;

import rw.rra.roomiq.identity.domain.entity.UserPrivilege;

import java.time.Instant;
import java.util.UUID;

public record PrivilegeResponse(
        UUID id,
        UUID userId,
        String privilegeCode,
        UUID grantedByUserId,
        Instant validFrom,
        Instant validTo,
        boolean active,
        boolean effectiveNow
) {
    public static PrivilegeResponse from(UserPrivilege privilege, Instant now) {
        return new PrivilegeResponse(privilege.getId(), privilege.getUser().getId(),
                privilege.getPrivilegeCode(),
                privilege.getGrantedBy() == null ? null : privilege.getGrantedBy().getId(),
                privilege.getValidFrom(), privilege.getValidTo(), privilege.isActive(),
                privilege.isEffectiveAt(now));
    }
}
