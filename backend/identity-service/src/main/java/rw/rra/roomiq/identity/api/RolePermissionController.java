package rw.rra.roomiq.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.identity.domain.dto.AssignUserRoleRequest;
import rw.rra.roomiq.identity.domain.dto.CreatePermissionRequest;
import rw.rra.roomiq.identity.domain.dto.CreateRoleRequest;
import rw.rra.roomiq.identity.domain.dto.PermissionResponse;
import rw.rra.roomiq.identity.domain.dto.RolePermissionResponse;
import rw.rra.roomiq.identity.domain.dto.RoleResponse;
import rw.rra.roomiq.identity.domain.dto.UserRoleResponse;
import rw.rra.roomiq.identity.domain.service.RolePermissionManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Roles and permissions", description = "Role and permission definitions and scoped user assignments")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request validation failed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The caller lacks the required permission or scope"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "A referenced role, permission, user, or assignment was not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The requested definition or assignment already exists")
})
public class RolePermissionController {
    private final RolePermissionManagementService service;

    public RolePermissionController(RolePermissionManagementService service) {
        this.service = service;
    }

    @PostMapping("/roles")
    @PreAuthorize("@identityAuthorization.canManageDefinitions(authentication)")
    @Operation(summary = "Create a role", description = "Create a role definition; restricted to global identity administrators.")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.success("Role created", service.createRole(request));
    }

    @GetMapping("/roles")
    @PreAuthorize("@identityAuthorization.canReadRoleDefinitions(authentication)")
    @Operation(summary = "List roles", description = "List role definitions when the caller has global or scoped role-read access.")
    public ApiResponse<List<RoleResponse>> listRoles() {
        return ApiResponse.success("Roles retrieved", service.listRoles());
    }

    @PostMapping("/permissions")
    @PreAuthorize("@identityAuthorization.canManageDefinitions(authentication)")
    @Operation(summary = "Create a permission", description = "Create a permission definition; restricted to global identity administrators.")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        return ApiResponse.success("Permission created", service.createPermission(request));
    }

    @GetMapping("/permissions")
    @PreAuthorize("@identityAuthorization.canReadPermissionDefinitions(authentication)")
    @Operation(summary = "List permissions", description = "List permission definitions when the caller has global or scoped permission-read access.")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        return ApiResponse.success("Permissions retrieved", service.listPermissions());
    }

    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    @PreAuthorize("@identityAuthorization.canManageDefinitions(authentication)")
    @Operation(summary = "Grant a permission to a role", description = "Attach a permission definition to a role; restricted to global identity administrators.")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RolePermissionResponse> grantPermission(@PathVariable UUID roleId,
                                                                 @PathVariable UUID permissionId) {
        return ApiResponse.success("Permission assigned to role", service.grantPermission(roleId, permissionId));
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("@identityAuthorization.canManageDefinitions(authentication)")
    @Operation(summary = "List role permissions", description = "List permissions attached to a role; restricted to global identity administrators.")
    public ApiResponse<List<RolePermissionResponse>> listRolePermissions(@PathVariable UUID roleId) {
        return ApiResponse.success("Role permissions retrieved", service.listRolePermissions(roleId));
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @PreAuthorize("@identityAuthorization.canManageDefinitions(authentication)")
    @Operation(summary = "Revoke a role permission", description = "Remove a permission from a role; restricted to global identity administrators.")
    public ApiResponse<Void> revokePermission(@PathVariable UUID roleId, @PathVariable UUID permissionId) {
        service.revokePermission(roleId, permissionId);
        return ApiResponse.success("Permission removed from role", null);
    }

    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("@identityAuthorization.canAssignRole(authentication, #userId, #request.roleId(), #request.scopeOfficeBuildingId())")
    @Operation(summary = "Assign a scoped role", description = "Assign an allowed role to a user within the caller's authorized building scope.")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserRoleResponse> assignRole(@PathVariable UUID userId,
                                                     @Valid @RequestBody AssignUserRoleRequest request) {
        return ApiResponse.success("Role assigned to user", service.assignRole(userId, request));
    }

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("@identityAuthorization.canReadUserRoles(authentication, #userId)")
    @Operation(summary = "List user roles", description = "List a user's role assignments when permitted by self-access or building scope.")
    public ApiResponse<List<UserRoleResponse>> listUserRoles(@PathVariable UUID userId) {
        return ApiResponse.success("User roles retrieved", service.listUserRoles(userId));
    }

    @DeleteMapping("/users/{userId}/roles/{assignmentId}")
    @PreAuthorize("@identityAuthorization.canRevokeUserRole(authentication, #userId, #assignmentId)")
    @Operation(summary = "Revoke a user role", description = "Remove an assignment the caller is authorized to revoke.")
    public ApiResponse<Void> revokeUserRole(@PathVariable UUID userId, @PathVariable UUID assignmentId) {
        service.revokeUserRole(userId, assignmentId);
        return ApiResponse.success("Role removed from user", null);
    }
}
