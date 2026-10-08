package rw.rra.roomiq.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.identity.domain.dto.ChangeUserStatusRequest;
import rw.rra.roomiq.identity.domain.dto.CreateUserRequest;
import rw.rra.roomiq.identity.domain.dto.UpdateUserRequest;
import rw.rra.roomiq.identity.domain.dto.UserListQuery;
import rw.rra.roomiq.identity.domain.dto.UserListResponse;
import rw.rra.roomiq.identity.domain.dto.UserResponse;
import rw.rra.roomiq.identity.domain.service.UserManagementService;
import rw.rra.roomiq.identity.domain.security.IdentityAuthorizationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Scoped user lifecycle and profile operations")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request validation failed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The caller lacks the required permission or scope"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User was not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A user identifier conflicts with an existing account")
})
public class UserController {
    private final UserManagementService userService;
    private final IdentityAuthorizationService authorization;

    public UserController(UserManagementService userService, IdentityAuthorizationService authorization) {
        this.userService = userService;
        this.authorization = authorization;
    }

    @PostMapping
    @PreAuthorize("@identityAuthorization.canCreateUser(authentication, #request.officeBuildingId())")
    @Operation(summary = "Create a user", description = "Create a pending user in a scope the caller is authorized to manage.")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("User created", userService.create(request));
    }

    @GetMapping
    @PreAuthorize("@identityAuthorization.canListUsers(authentication, #query.officeBuildingId())")
    @Operation(summary = "List users", description = "Search and page users visible under the caller's current role, permissions, and building scope.")
    public ApiResponse<UserListResponse> list(@Valid @ModelAttribute UserListQuery query,
                                               Authentication authentication) {
        boolean scopedAdminView = !authorization.isSystemAdministrator(authentication);
        return ApiResponse.success("Users retrieved", userService.list(query, scopedAdminView));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@identityAuthorization.canReadUser(authentication, #id)")
    @Operation(summary = "Get a user", description = "Read the caller's own profile or another user permitted by the caller's scope.")
    public ApiResponse<UserResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("User retrieved", userService.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@identityAuthorization.canUpdateUser(authentication, #id, #request.departmentId(), #request.officeBuildingId())")
    @Operation(summary = "Update a user", description = "Partially update an authorized user profile without allowing an out-of-scope move.")
    public ApiResponse<UserResponse> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("User updated", userService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@identityAuthorization.canManageUser(authentication, #id)")
    @Operation(summary = "Change user status", description = "Change the lifecycle status of a user the caller is authorized to manage.")
    public ApiResponse<UserResponse> changeStatus(@PathVariable UUID id,
                                                   @Valid @RequestBody ChangeUserStatusRequest request) {
        return ApiResponse.success("User status updated", userService.changeStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@identityAuthorization.canManageUser(authentication, #id)")
    @Operation(summary = "Soft-delete a user", description = "Disable and soft-delete a user while retaining identity history.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ApiResponse.success("User deleted", null);
    }
}