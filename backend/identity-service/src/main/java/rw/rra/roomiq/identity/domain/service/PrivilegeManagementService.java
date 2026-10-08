package rw.rra.roomiq.identity.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.identity.domain.dto.GrantPrivilegeRequest;
import rw.rra.roomiq.identity.domain.dto.PrivilegeResponse;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.UserPrivilege;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;
import rw.rra.roomiq.identity.domain.repository.UserPrivilegeRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PrivilegeManagementService {
    private final AppUserRepository userRepository;
    private final UserPrivilegeRepository privilegeRepository;
    private final IdentityAuditService auditService;

    public PrivilegeManagementService(AppUserRepository userRepository,
                                      UserPrivilegeRepository privilegeRepository,
                                      IdentityAuditService auditService) {
        this.userRepository = userRepository;
        this.privilegeRepository = privilegeRepository;
        this.auditService = auditService;
    }

        public PrivilegeResponse grant(UUID userId, GrantPrivilegeRequest request, UUID grantedByUserId) {
        AppUser user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> notFound("USER_NOT_FOUND", "User was not found"));
        AppUser grantedBy = grantedByUserId == null ? null
            : userRepository.findById(grantedByUserId)
            .orElseThrow(() -> notFound("GRANTING_USER_NOT_FOUND", "Granting user was not found"));
        validateWindow(request.validFrom(), request.validTo());

        String code = request.privilegeCode().trim();
        boolean overlaps = privilegeRepository.findAllByUserIdAndPrivilegeCodeIgnoreCaseAndActiveTrue(userId, code).stream()
                .anyMatch(existing -> overlaps(existing.getValidFrom(), existing.getValidTo(),
                        request.validFrom(), request.validTo()));
        if (overlaps) {
            throw new DomainException(HttpStatus.CONFLICT, "PRIVILEGE_GRANT_CONFLICT",
                "An overlapping grant for this privilege already exists");
        }

        UserPrivilege privilege = new UserPrivilege(user, code, grantedBy,
                request.validFrom(), request.validTo(), true);
        PrivilegeResponse response = PrivilegeResponse.from(privilegeRepository.saveAndFlush(privilege), Instant.now());
        auditService.record("IDENTITY_PRIVILEGE_GRANTED", grantedByUserId, "USER_PRIVILEGE", response.id(),
            user.getOfficeBuildingId(), "SUCCESS", java.util.Map.of("privilegeCode", code));
        return response;
    }

    @Transactional(readOnly = true)
    public List<PrivilegeResponse> list(UUID userId) {
        requireUser(userId);
        Instant now = Instant.now();
        return privilegeRepository.findAllByUserIdAndActiveTrue(userId).stream()
                .map(privilege -> PrivilegeResponse.from(privilege, now)).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasPrivilege(UUID userId, String privilegeCode, Instant at) {
        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> notFound("USER_NOT_FOUND", "User was not found"));
        if (user.getStatus() != rw.rra.roomiq.identity.domain.entity.UserStatus.ACTIVE) {
            return false;
        }
        Instant evaluationTime = at == null ? Instant.now() : at;
        return privilegeRepository.findAllByUserIdAndPrivilegeCodeAndActiveTrue(userId, privilegeCode.trim())
                .stream().anyMatch(privilege -> privilege.isEffectiveAt(evaluationTime));
    }

    public void revoke(UUID userId, UUID privilegeId) {
        requireUser(userId);
        UserPrivilege privilege = privilegeRepository.findById(privilegeId)
                .filter(candidate -> candidate.getUser().getId().equals(userId))
            .orElseThrow(() -> notFound("USER_PRIVILEGE_NOT_FOUND", "User privilege was not found"));
        if (!privilege.isActive()) {
            throw new DomainException(HttpStatus.CONFLICT, "PRIVILEGE_ALREADY_INACTIVE",
                "User privilege is already inactive");
        }
        privilege.deactivate();
        privilegeRepository.saveAndFlush(privilege);
        auditService.recordCurrentActor("IDENTITY_PRIVILEGE_REVOKED", "USER_PRIVILEGE", privilegeId,
            privilege.getUser().getOfficeBuildingId(), "SUCCESS",
            java.util.Map.of("privilegeCode", privilege.getPrivilegeCode()));
    }

    private void requireUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw notFound("USER_NOT_FOUND", "User was not found");
        }
    }

    private void validateWindow(Instant validFrom, Instant validTo) {
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
                throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_PRIVILEGE_VALIDITY_WINDOW",
                    "validTo must be later than validFrom");
        }
    }

    private boolean overlaps(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        return (firstEnd == null || secondStart == null || secondStart.isBefore(firstEnd))
                && (secondEnd == null || firstStart == null || firstStart.isBefore(secondEnd));
    }

    private DomainException notFound(String code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }
}
