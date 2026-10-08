package rw.rra.roomiq.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.identity.domain.dto.GrantPrivilegeRequest;
import rw.rra.roomiq.identity.domain.dto.PrivilegeResponse;
import rw.rra.roomiq.identity.domain.security.IdentityAuthorizationService;
import rw.rra.roomiq.identity.domain.service.PrivilegeManagementService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/users/{userId}/privileges")
@Tag(name = "User privileges", description = "Time-bounded user privileges such as CG_BOOKING")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request validation failed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication is required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "The caller lacks the required permission or scope"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User or privilege was not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The grant conflicts with an existing active validity window")
})
public class PrivilegeController {
    private final PrivilegeManagementService privilegeService;
    private final IdentityAuthorizationService authorization;

    public PrivilegeController(PrivilegeManagementService privilegeService,
                               IdentityAuthorizationService authorization) {
        this.privilegeService = privilegeService;
        this.authorization = authorization;
    }

    @PostMapping
    @PreAuthorize("@identityAuthorization.canManagePrivilege(authentication, #userId, #request.privilegeCode())")
    @Operation(summary = "Grant a user privilege", description = "Grant a supported privilege with optional validity bounds; grant attribution is taken from the authenticated actor.")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PrivilegeResponse> grant(@PathVariable UUID userId,
                                                 @Valid @RequestBody GrantPrivilegeRequest request,
                                                 Authentication authentication) {
                    return ApiResponse.success("Privilege granted", privilegeService.grant(userId, request,
                        authorization.authenticatedUserId(authentication)));
    }

    @GetMapping
    @PreAuthorize("@identityAuthorization.canReadPrivileges(authentication, #userId)")
    @Operation(summary = "List user privileges", description = "List the active privilege grants visible under the caller's authorization scope.")
    public ApiResponse<List<PrivilegeResponse>> list(@PathVariable UUID userId) {
        return ApiResponse.success("User privileges retrieved", privilegeService.list(userId));
    }

    @GetMapping("/evaluate")
    @PreAuthorize("@identityAuthorization.canReadPrivileges(authentication, #userId)")
    @Operation(summary = "Evaluate a user privilege", description = "Evaluate whether a privilege is effective at the requested instant.")
    public ApiResponse<Boolean> evaluate(
            @PathVariable UUID userId,
            @RequestParam @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,127}") String privilegeCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return ApiResponse.success("Privilege evaluated", privilegeService.hasPrivilege(userId, privilegeCode, at));
    }

    @DeleteMapping("/{privilegeId}")
    @PreAuthorize("@identityAuthorization.canRevokePrivilege(authentication, #userId, #privilegeId)")
    @Operation(summary = "Revoke a user privilege", description = "Deactivate a privilege grant while preserving its history.")
    public ApiResponse<Void> revoke(@PathVariable UUID userId, @PathVariable UUID privilegeId) {
        privilegeService.revoke(userId, privilegeId);
        return ApiResponse.success("Privilege revoked", null);
    }
}
