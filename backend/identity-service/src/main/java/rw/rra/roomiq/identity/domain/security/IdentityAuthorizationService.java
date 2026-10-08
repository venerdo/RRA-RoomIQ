package rw.rra.roomiq.identity.domain.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.Role;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;
import rw.rra.roomiq.identity.domain.repository.IdentityAuthorizationRepository;
import rw.rra.roomiq.identity.domain.repository.RoleRepository;
import rw.rra.roomiq.identity.domain.repository.UserPrivilegeRepository;
import rw.rra.roomiq.identity.domain.repository.UserRoleRepository;

import java.util.UUID;
import java.time.Instant;

import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.PRIVILEGE_GRANT;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.PRIVILEGE_READ;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.PERMISSION_READ;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.ROLE_READ;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.SYSTEM_ADMIN;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.USER_MANAGE;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.USER_READ;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.USER_ROLE_ASSIGN;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.USER_ROLE_READ;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.BOOKING_APPROVE;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.BOOKING_CANCEL;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.BOOKING_CANCEL_OWN;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.BOOKING_DIRECT_CREATE;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.BOOKING_EXTENSION_DECIDE;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.BOOKING_EXTENSION_REQUEST;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.BOOKING_REQUEST_CREATE;
import static rw.rra.roomiq.identity.domain.security.IdentityPermissionCodes.ROOM_MANAGE;

@Service("identityAuthorization")
@Transactional(readOnly = true)
public class IdentityAuthorizationService {
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final IdentityAuthorizationRepository authorization;
    private final UserPrivilegeRepository privileges;
    private final UserRoleRepository userRoles;

    public IdentityAuthorizationService(AppUserRepository users, RoleRepository roles,
                                        IdentityAuthorizationRepository authorization,
                                        UserPrivilegeRepository privileges,
                                        UserRoleRepository userRoles) {
        this.users = users;
        this.roles = roles;
        this.authorization = authorization;
        this.privileges = privileges;
        this.userRoles = userRoles;
    }

    public boolean canListUsers(Authentication authentication, UUID requestedBuildingId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        return requestedBuildingId != null
                && hasPermission(authentication, actorId, USER_READ, requestedBuildingId);
    }

    public boolean canReadUser(Authentication authentication, UUID targetUserId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (actorId.equals(targetUserId) || hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        AppUser target = users.findById(targetUserId).orElse(null);
        return target != null && target.getOfficeBuildingId() != null
            && isSecretaryOrUnassignedPending(target)
                && hasPermission(authentication, actorId, USER_READ, target.getOfficeBuildingId());
    }

    public boolean canManageUser(Authentication authentication, UUID targetUserId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        AppUser target = users.findById(targetUserId).orElse(null);
        return target != null && target.getOfficeBuildingId() != null
            && isSecretaryOrUnassignedPending(target)
                && hasPermission(authentication, actorId, USER_MANAGE, target.getOfficeBuildingId());
    }

    public boolean canCreateUser(Authentication authentication, UUID buildingId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId)
                && (hasGlobalPermission(authentication, actorId)
                || (buildingId != null && hasPermission(authentication, actorId, USER_MANAGE, buildingId)));
    }

    public boolean canUpdateUser(Authentication authentication, UUID targetUserId,
                                 UUID requestedDepartmentId, UUID requestedOfficeBuildingId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        AppUser user = users.findById(targetUserId).orElse(null);
        if (user == null) {
            return false;
        }
        boolean departmentUnchanged = requestedDepartmentId == null
                || requestedDepartmentId.equals(user.getDepartmentId());
        boolean officeUnchanged = requestedOfficeBuildingId == null
                || requestedOfficeBuildingId.equals(user.getOfficeBuildingId());

        if (actorId.equals(targetUserId)) {
            return departmentUnchanged && officeUnchanged;
        }

        if (!isSecretaryOrUnassignedPending(user)) {
            return false;
        }

        UUID currentBuilding = user.getOfficeBuildingId();
        if (currentBuilding == null || !hasPermission(authentication, actorId, USER_MANAGE, currentBuilding)) {
            return false;
        }
        boolean destinationInScope = requestedOfficeBuildingId == null
                || requestedOfficeBuildingId.equals(currentBuilding)
                || hasPermission(authentication, actorId, USER_MANAGE, requestedOfficeBuildingId);
        return destinationInScope && departmentUnchanged;
    }

    public boolean canManageDefinitions(Authentication authentication) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId) && hasGlobalPermission(authentication, actorId);
    }

    public boolean isSystemAdministrator(Authentication authentication) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId) && hasGlobalPermission(authentication, actorId);
    }

    public boolean canReadOrganization(Authentication authentication) {
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId);
    }

    public boolean canManageOrganization(Authentication authentication) {
        return isSystemAdministrator(authentication);
    }

    public boolean canReadScheduling(Authentication authentication) {
        return canReadOrganization(authentication);
    }

    public boolean canManageScheduling(Authentication authentication) {
        return isSystemAdministrator(authentication);
    }

    public boolean canAssignRole(Authentication authentication, UUID targetUserId, UUID roleId,
                                 UUID requestedBuildingId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        Role role = roles.findById(roleId).orElse(null);
        AppUser target = users.findById(targetUserId).orElse(null);
        if (role == null || target == null) {
            return false;
        }
        boolean globalAdmin = hasGlobalPermission(authentication, actorId);
        if (isPrivilegedRole(role) && !globalAdmin) {
            return false;
        }
        if (globalAdmin) {
            return true;
        }
        if (!isSecretaryOrUnassignedPending(target)) {
            return false;
        }
        if (userRoles.findAllByUserId(targetUserId).stream()
                .anyMatch(assignment -> isPrivilegedRole(assignment.getRole()))) {
            return false;
        }
        if (!role.getCode().equals("SECRETARY")) {
            return false;
        }
        return requestedBuildingId != null
                && requestedBuildingId.equals(target.getOfficeBuildingId())
                && hasPermission(authentication, actorId, USER_ROLE_ASSIGN, requestedBuildingId);
    }

    public boolean canReadUserRoles(Authentication authentication, UUID targetUserId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (actorId.equals(targetUserId) || hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        AppUser target = users.findById(targetUserId).orElse(null);
        return target != null && target.getOfficeBuildingId() != null
                && isSecretaryOrUnassignedPending(target)
                && hasPermission(authentication, actorId, USER_ROLE_READ, target.getOfficeBuildingId());
    }

    public boolean canManageUserRoles(Authentication authentication, UUID targetUserId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        AppUser target = users.findById(targetUserId).orElse(null);
        return target != null && target.getOfficeBuildingId() != null
                && isSecretaryOrUnassignedPending(target)
                && hasPermission(authentication, actorId, USER_ROLE_ASSIGN, target.getOfficeBuildingId());
    }

    public boolean canAssignUserRole(Authentication authentication, UUID targetUserId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        AppUser target = users.findById(targetUserId).orElse(null);
        return target != null && target.getOfficeBuildingId() != null
                && hasPermission(authentication, actorId, USER_ROLE_ASSIGN, target.getOfficeBuildingId());
    }

    public boolean canRevokeUserRole(Authentication authentication, UUID targetUserId, UUID assignmentId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        var assignment = userRoles.findByIdAndUserId(assignmentId, targetUserId).orElse(null);
        AppUser target = users.findById(targetUserId).orElse(null);
        if (assignment == null || target == null || target.getOfficeBuildingId() == null
                || !assignment.getRole().getCode().equals("SECRETARY")
                || !isSecretaryOrUnassignedPending(target)) {
            return false;
        }
        return hasPermission(authentication, actorId, USER_ROLE_ASSIGN, target.getOfficeBuildingId());
    }

    public boolean canReadRoleDefinitions(Authentication authentication) {
        return canReadGlobalDefinitions(authentication, ROLE_READ);
    }

    public boolean canReadPermissionDefinitions(Authentication authentication) {
        return canReadGlobalDefinitions(authentication, PERMISSION_READ);
    }

    public boolean canManagePrivilege(Authentication authentication, UUID targetUserId, String privilegeCode) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        AppUser target = users.findById(targetUserId).orElse(null);
        return target != null && target.getOfficeBuildingId() != null
            && isSecretaryOrUnassignedPending(target)
                && "CG_BOOKING".equals(privilegeCode)
                && hasPermission(authentication, actorId, PRIVILEGE_GRANT, target.getOfficeBuildingId());
    }

    public boolean canRevokePrivilege(Authentication authentication, UUID targetUserId, UUID privilegeId) {
        var privilege = privileges.findById(privilegeId).orElse(null);
        return privilege != null && privilege.getUser().getId().equals(targetUserId)
                && canManagePrivilege(authentication, targetUserId, privilege.getPrivilegeCode());
    }

    public boolean canReadPrivileges(Authentication authentication, UUID targetUserId) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId)) {
            return false;
        }
        if (actorId.equals(targetUserId) || hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        AppUser target = users.findById(targetUserId).orElse(null);
        return target != null && target.getOfficeBuildingId() != null
            && isSecretaryOrUnassignedPending(target)
            && hasPermission(authentication, actorId, PRIVILEGE_READ, target.getOfficeBuildingId());
    }

    public boolean canRequestBooking(Authentication authentication, UUID requestedByUserId,
                                     UUID departmentId, UUID buildingId, boolean vipRoom, Instant at) {
        UUID actorId = actorId(authentication);
        if (actorId == null || !actorId.equals(requestedByUserId) || buildingId == null || !isActive(actorId)) {
            return false;
        }
        if (hasGlobalPermission(authentication, actorId)
                || hasPermission(authentication, actorId, BOOKING_DIRECT_CREATE, buildingId)) {
            return true;
        }
        AppUser user = users.findById(actorId).orElse(null);
        if (user == null || !buildingId.equals(user.getOfficeBuildingId())
                || departmentId == null || !departmentId.equals(user.getDepartmentId())
                || !hasPermission(authentication, actorId, BOOKING_REQUEST_CREATE, buildingId)) {
            return false;
        }
        return !vipRoom || hasActivePrivilege(actorId, "CG_BOOKING", at == null ? Instant.now() : at);
    }

    public boolean canApproveBooking(Authentication authentication, UUID requesterUserId, UUID buildingId) {
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId) || actorId.equals(requesterUserId) || buildingId == null) {
            return false;
        }
        return hasGlobalPermission(authentication, actorId)
                || hasPermission(authentication, actorId, BOOKING_APPROVE, buildingId);
    }

    public boolean canDirectBook(Authentication authentication, UUID buildingId) {
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId) && buildingId != null
                && (hasGlobalPermission(authentication, actorId)
                || hasPermission(authentication, actorId, BOOKING_DIRECT_CREATE, buildingId));
    }

    public boolean canCancelBooking(Authentication authentication, UUID organizerUserId, UUID buildingId) {
        UUID actorId = actorId(authentication);
        if (actorId == null || !isActive(actorId) || buildingId == null) {
            return false;
        }
        if (hasGlobalPermission(authentication, actorId)) {
            return true;
        }
        if (actorId.equals(organizerUserId)) {
            return hasPermission(authentication, actorId, BOOKING_CANCEL_OWN, buildingId);
        }
        return hasPermission(authentication, actorId, BOOKING_CANCEL, buildingId);
    }

    public boolean canRequestBookingExtension(Authentication authentication, UUID requesterUserId,
                                              UUID buildingId) {
        UUID actorId = actorId(authentication);
        return actorId != null && actorId.equals(requesterUserId) && isActive(actorId)
                && buildingId != null && hasPermission(authentication, actorId, BOOKING_EXTENSION_REQUEST, buildingId);
    }

    public boolean canDecideBookingExtension(Authentication authentication, UUID requesterUserId,
                                             UUID buildingId) {
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId) && !actorId.equals(requesterUserId) && buildingId != null
                && (hasGlobalPermission(authentication, actorId)
                || hasPermission(authentication, actorId, BOOKING_EXTENSION_DECIDE, buildingId));
    }

    public boolean canManageRooms(Authentication authentication, UUID buildingId) {
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId) && buildingId != null
                && (hasGlobalPermission(authentication, actorId)
                || hasPermission(authentication, actorId, ROOM_MANAGE, buildingId));
    }

    public boolean canManageRoomCatalog(Authentication authentication) {
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId) && hasGlobalPermission(authentication, actorId);
    }

    private boolean hasGlobalPermission(Authentication authentication, UUID actorId) {
        return hasExplicitAuthority(authentication, SYSTEM_ADMIN)
                || authorization.hasPermissionAtBuilding(actorId, SYSTEM_ADMIN, null);
    }

    private boolean canReadGlobalDefinitions(Authentication authentication, String permissionCode) {
        if (hasExplicitAuthority(authentication, SYSTEM_ADMIN)) {
            return true;
        }
        UUID actorId = actorId(authentication);
        return actorId != null && isActive(actorId)
                && (hasGlobalPermission(authentication, actorId)
                || !authorization.findAuthorizedBuildingScopes(actorId, permissionCode).isEmpty());
    }

    private boolean hasPermission(Authentication authentication, UUID actorId, String permissionCode,
                                  UUID buildingId) {
        return hasExplicitAuthority(authentication, permissionCode)
                || hasGlobalPermission(authentication, actorId)
                || authorization.hasPermissionAtBuilding(actorId, permissionCode, buildingId);
    }

    private boolean hasExplicitAuthority(Authentication authentication, String permissionCode) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permissionCode::equals);
    }

    private UUID actorId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            try {
                return UUID.fromString(jwtAuthentication.getToken().getSubject());
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                return users.findByEmailIgnoreCase(authentication.getName())
                        .map(AppUser::getId).orElse(null);
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    public UUID authenticatedUserId(Authentication authentication) {
        return actorId(authentication);
    }

    private boolean isActive(UUID userId) {
        return users.findById(userId).map(user -> user.getStatus().name().equals("ACTIVE")).orElse(false);
    }

    private boolean hasActivePrivilege(UUID userId, String privilegeCode, Instant at) {
        return privileges.findAllByUserIdAndPrivilegeCodeAndActiveTrue(userId, privilegeCode).stream()
                .anyMatch(privilege -> privilege.isEffectiveAt(at));
    }

    private boolean isPrivilegedRole(Role role) {
        return role.getCode().equals("ADMIN") || role.getCode().equals("SUPER_ADMIN");
    }

    private boolean isSecretaryOrUnassignedPending(AppUser user) {
        var assignments = userRoles.findAllByUserId(user.getId());
        if (assignments.isEmpty()) {
            return user.getStatus().name().equals("PENDING");
        }
        if (assignments.stream().anyMatch(assignment -> isPrivilegedRole(assignment.getRole()))) {
            return false;
        }
        UUID officeBuildingId = user.getOfficeBuildingId();
        return officeBuildingId != null && assignments.stream().anyMatch(assignment ->
                assignment.getRole().getCode().equals("SECRETARY")
                        && officeBuildingId.equals(assignment.getScopeOfficeBuildingId()));
    }
}
