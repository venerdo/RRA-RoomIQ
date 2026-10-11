package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.PublicMeetingCardResponse;
import rw.rra.roomiq.booking.domain.service.MeetingShareLinkService;
import rw.rra.roomiq.common.web.ApiResponse;

@RestController
@RequestMapping("/api/v1/public/meetings")
@Tag(name = "Public meeting cards")
public class PublicMeetingCardController {
    private final MeetingShareLinkService service;

    public PublicMeetingCardController(MeetingShareLinkService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    @SecurityRequirements
    @Operation(summary = "Read a shared public meeting card",
            description = "The opaque token is the only unauthenticated Booking data flow. The response contains only title, start/end timestamps, IANA timezone, and organizer display name. Requests are rate-limited; invalid, expired, and revoked tokens are indistinguishable.")
    public ApiResponse<PublicMeetingCardResponse> get(@PathVariable String token) {
        return ApiResponse.success("Public meeting card retrieved", service.readPublicCard(token));
    }
}
