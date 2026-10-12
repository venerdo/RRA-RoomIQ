package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import rw.rra.roomiq.booking.domain.dto.CreateMeetingShareLinkRequest;
import rw.rra.roomiq.booking.domain.dto.MeetingShareLinkCreatedResponse;
import rw.rra.roomiq.booking.domain.dto.MeetingShareLinkResponse;
import rw.rra.roomiq.booking.domain.service.MeetingShareLinkService;
import rw.rra.roomiq.common.web.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Meeting share links")
@SecurityRequirement(name = "bearerAuth")
public class MeetingShareLinkController {
    private final MeetingShareLinkService service;

    public MeetingShareLinkController(MeetingShareLinkService service) {
        this.service = service;
    }

    @PostMapping("/meetings/{meetingId}/share-links")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an opaque meeting share link",
            description = "Requires current Identity meeting-management scope and an Idempotency-Key. Only approved bookings with confirmed reservations qualify. Equivalent retries return the same token with replayed=true; reusing a key with a different meeting or expiry returns 409. The raw, unguessable token is returned only in this protected response; only its SHA-256 hash is persisted.")
    public ApiResponse<MeetingShareLinkCreatedResponse> create(
            @PathVariable UUID meetingId,
            @Parameter(description = "Required 1-128 character idempotency key; equivalent retries replay the same share token")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateMeetingShareLinkRequest request) {
        return ApiResponse.success("Meeting share link created",
                service.create(meetingId, request, idempotencyKey));
    }

    @PostMapping("/meeting-share-links/{shareLinkId}/revoke")
    @Operation(summary = "Revoke a meeting share link",
            description = "Requires current Identity meeting-management scope. Revocation is idempotent and prevents later public-card reads.")
    public ApiResponse<MeetingShareLinkResponse> revoke(@PathVariable UUID shareLinkId) {
        return ApiResponse.success("Meeting share link revoked", service.revoke(shareLinkId));
    }
}
