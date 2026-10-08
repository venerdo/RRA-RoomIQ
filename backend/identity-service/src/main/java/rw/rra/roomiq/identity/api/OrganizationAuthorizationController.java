package rw.rra.roomiq.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.identity.domain.dto.OrganizationAuthorizationRequest;
import rw.rra.roomiq.identity.domain.dto.RoomManagementAuthorizationRequest;
import rw.rra.roomiq.identity.domain.dto.RoomManagementAuthorizationResponse;
import rw.rra.roomiq.identity.domain.dto.SchedulingAuthorizationRequest;
import rw.rra.roomiq.identity.domain.dto.SchedulingAuthorizationResponse;
import rw.rra.roomiq.identity.domain.security.IdentityAuthorizationService;

@RestController
@RequestMapping("/api/v1/internal/authorization")
@Tag(name = "Internal authorization")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationAuthorizationController {
    private final IdentityAuthorizationService authorization;

    public OrganizationAuthorizationController(IdentityAuthorizationService authorization) {
        this.authorization = authorization;
    }

    @PostMapping("/organization")
    @Operation(summary = "Authorize organization access", description = "Evaluate the authenticated user's current access to Organization Service APIs.")
    public ResponseEntity<Void> authorizeOrganization(
            @Valid @RequestBody OrganizationAuthorizationRequest request,
            Authentication authentication) {
        boolean allowed = switch (request.action()) {
            case READ -> authorization.canReadOrganization(authentication);
            case MANAGE -> authorization.canManageOrganization(authentication);
        };
        if (!allowed) {
            throw new AccessDeniedException("Organization access is denied");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/scheduling")
    @Operation(summary = "Authorize scheduling access", description = "Active users may read scheduling data; calendar and working-window mutations require system-admin authority.")
    public ResponseEntity<SchedulingAuthorizationResponse> authorizeScheduling(
            @Valid @RequestBody SchedulingAuthorizationRequest request,
            Authentication authentication) {
        boolean allowed = switch (request.action()) {
            case READ -> authorization.canReadScheduling(authentication);
            case MANAGE -> authorization.canManageScheduling(authentication);
        };
        if (!allowed) {
            throw new AccessDeniedException("Scheduling access is denied");
        }
        return ResponseEntity.ok(new SchedulingAuthorizationResponse(
            authorization.authenticatedUserId(authentication)));
    }

    @PostMapping("/room-management")
    @Operation(summary = "Authorize room management", description = "Evaluate ROOM_MANAGE for a building-scoped mutation, or global system administration for an unscoped room catalog mutation; return the authenticated actor ID.")
    public ResponseEntity<RoomManagementAuthorizationResponse> authorizeRoomManagement(
            @Valid @RequestBody RoomManagementAuthorizationRequest request,
            Authentication authentication) {
        boolean allowed = request.officeBuildingId() == null
                ? authorization.canManageRoomCatalog(authentication)
                : authorization.canManageRooms(authentication, request.officeBuildingId());
        var actorUserId = authorization.authenticatedUserId(authentication);
        if (!allowed || actorUserId == null) {
            throw new AccessDeniedException("Room management is denied");
        }
        return ResponseEntity.ok(new RoomManagementAuthorizationResponse(actorUserId));
    }
}