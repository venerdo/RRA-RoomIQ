package rw.rra.roomiq.identity.domain.service;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.identity.domain.dto.AssignUserRoleRequest;
import rw.rra.roomiq.identity.domain.dto.CreatePermissionRequest;
import rw.rra.roomiq.identity.domain.dto.CreateRoleRequest;
import rw.rra.roomiq.identity.domain.dto.PermissionResponse;
import rw.rra.roomiq.identity.domain.dto.RolePermissionResponse;
import rw.rra.roomiq.identity.domain.dto.RoleResponse;
import rw.rra.roomiq.identity.domain.dto.UserRoleResponse;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.Permission;
import rw.rra.roomiq.identity.domain.entity.Role;
import rw.rra.roomiq.identity.domain.entity.RolePermission;
import rw.rra.roomiq.identity.domain.entity.UserRole;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;
import rw.rra.roomiq.identity.domain.repository.PermissionRepository;
import rw.rra.roomiq.identity.domain.repository.RolePermissionRepository;
import rw.rra.roomiq.identity.domain.repository.RoleRepository;
import rw.rra.roomiq.identity.domain.repository.UserRoleRepository;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RolePermissionManagementService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AppUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final IdentityAuditService auditService;

    public RolePermissionManagementService(RoleRepository roleRepository,
                                           PermissionRepository permissionRepository,
                                           RolePermissionRepository rolePermissionRepository,
                                           AppUserRepository userRepository,
                                           UserRoleRepository userRoleRepository,
                                           IdentityAuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
    }

    public RoleResponse createRole(CreateRoleRequest request) {
        String code = request.code().trim();
        if (roleRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw conflict("ROLE_CODE_CONFLICT", "Role code already exists");
        }
        Role role = new Role(code, request.name().trim(), normalize(request.description()), Boolean.TRUE.equals(request.system()));
        RoleResponse response = RoleResponse.from(roleRepository.saveAndFlush(role));
        auditService.recordCurrentActor("IDENTITY_ROLE_CREATED", "ROLE", response.id(), null, "SUCCESS");
        return response;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll(Sort.by(Sort.Direction.ASC, "code")).stream()
                .map(RoleResponse::from).toList();
    }

    public PermissionResponse createPermission(CreatePermissionRequest request) {
        String code = request.code().trim();
        if (permissionRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw conflict("PERMISSION_CODE_CONFLICT", "Permission code already exists");
        }
        Permission permission = new Permission(code, request.name().trim(), normalize(request.domain()));
        PermissionResponse response = PermissionResponse.from(permissionRepository.saveAndFlush(permission));
        auditService.recordCurrentActor("IDENTITY_PERMISSION_CREATED", "PERMISSION", response.id(), null, "SUCCESS");
        return response;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAll(Sort.by(Sort.Direction.ASC, "code")).stream()
                .map(PermissionResponse::from).toList();
    }

    public RolePermissionResponse grantPermission(UUID roleId, UUID permissionId) {
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> notFound("ROLE_NOT_FOUND", "Role was not found"));
        Permission permission = permissionRepository.findById(permissionId)
            .orElseThrow(() -> notFound("PERMISSION_NOT_FOUND", "Permission was not found"));
        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            throw conflict("ROLE_PERMISSION_CONFLICT", "Permission is already assigned to this role");
        }
        RolePermission assignment = rolePermissionRepository.saveAndFlush(new RolePermission(role, permission));
        RolePermissionResponse response = RolePermissionResponse.from(assignment);
        auditService.recordCurrentActor("IDENTITY_ROLE_PERMISSION_GRANTED", "ROLE_PERMISSION",
            response.assignmentId(), null, "SUCCESS");
        return response;
    }

    @Transactional(readOnly = true)
    public List<RolePermissionResponse> listRolePermissions(UUID roleId) {
        if (!roleRepository.existsById(roleId)) {
            throw notFound("ROLE_NOT_FOUND", "Role was not found");
        }
        return rolePermissionRepository.findAllByRoleId(roleId).stream()
                .map(RolePermissionResponse::from).toList();
    }

    public void revokePermission(UUID roleId, UUID permissionId) {
        long deleted = rolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
        if (deleted == 0) {
            throw notFound("ROLE_PERMISSION_NOT_FOUND", "Role permission assignment was not found");
        }
        auditService.recordCurrentActor("IDENTITY_ROLE_PERMISSION_REVOKED", "ROLE", roleId, null, "SUCCESS",
            java.util.Map.of("permissionId", permissionId.toString()));
    }

    public UserRoleResponse assignRole(UUID userId, AssignUserRoleRequest request) {
        AppUser user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> notFound("USER_NOT_FOUND", "User was not found"));
        Role role = roleRepository.findById(request.roleId())
            .orElseThrow(() -> notFound("ROLE_NOT_FOUND", "Role was not found"));
        if (userRoleRepository.existsByUserIdAndRoleIdAndScopeOfficeBuildingId(
                userId, role.getId(), request.scopeOfficeBuildingId())) {
                throw conflict("USER_ROLE_CONFLICT", "Role is already assigned to this user in the requested scope");
        }
        UserRole assignment = userRoleRepository.saveAndFlush(new UserRole(user, role,
                request.scopeOfficeBuildingId(), null));
            UserRoleResponse response = UserRoleResponse.from(assignment);
            auditService.recordCurrentActor("IDENTITY_USER_ROLE_ASSIGNED", "USER_ROLE", response.assignmentId(),
                request.scopeOfficeBuildingId(), "SUCCESS");
            return response;
    }

    @Transactional(readOnly = true)
    public List<UserRoleResponse> listUserRoles(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw notFound("USER_NOT_FOUND", "User was not found");
        }
        return userRoleRepository.findAllByUserId(userId).stream()
                .map(UserRoleResponse::from).toList();
    }

    public void revokeUserRole(UUID userId, UUID assignmentId) {
        if (!userRepository.existsById(userId)) {
            throw notFound("USER_NOT_FOUND", "User was not found");
        }
        UserRole assignment = userRoleRepository.findByIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> notFound("USER_ROLE_ASSIGNMENT_NOT_FOUND", "User role assignment was not found"));
        java.util.UUID scopeOfficeBuildingId = assignment.getScopeOfficeBuildingId();
        userRoleRepository.delete(assignment);
        auditService.recordCurrentActor("IDENTITY_USER_ROLE_REVOKED", "USER_ROLE", assignmentId,
                scopeOfficeBuildingId, "SUCCESS");
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    private DomainException notFound(String code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
