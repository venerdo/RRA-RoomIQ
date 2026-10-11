package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.AddMeetingParticipantRequest;
import rw.rra.roomiq.booking.domain.dto.MeetingParticipantResponse;
import rw.rra.roomiq.booking.domain.dto.MeetingResponse;
import rw.rra.roomiq.booking.domain.service.MeetingAccessService;
import rw.rra.roomiq.common.web.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings")
@Tag(name = "Meetings")
@SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401", description = "Authentication required")
public class MeetingController {
    private final MeetingAccessService service;

    public MeetingController(MeetingAccessService service) {
        this.service = service;
    }

    @GetMapping("/{meetingId}")
    @Operation(summary = "Read an authorized meeting",
            description = "PRIVATE and not-yet-shareable PUBLIC meetings are readable by the organizer and in-scope Booking reviewers. INTERNAL meetings are also readable by active users assigned to the meeting building. All access requires bearer authentication; public-card access is deferred to the share-link workflow.")
    public ApiResponse<MeetingResponse> getMeeting(@PathVariable UUID meetingId) {
        return ApiResponse.success("Meeting retrieved", service.getMeeting(meetingId));
    }

    @GetMapping("/{meetingId}/participants")
    @Operation(summary = "List meeting participants",
            description = "Participant identities and external contact details use the same server-side visibility and scope decision as the meeting.")
    public ApiResponse<List<MeetingParticipantResponse>> listParticipants(@PathVariable UUID meetingId) {
        return ApiResponse.success("Meeting participants retrieved", service.listParticipants(meetingId));
    }

    @PostMapping("/{meetingId}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a meeting participant",
            description = "Only the organizer or a current in-scope reviewer may manage participants. Internal users must be active Identity accounts assigned to this building; their display name is derived from Identity. External participants require an email and display name. Organizer attribution and invitation status are server-controlled.")
    public ApiResponse<MeetingParticipantResponse> addParticipant(
            @PathVariable UUID meetingId,
            @Valid @RequestBody AddMeetingParticipantRequest request) {
        return ApiResponse.success("Meeting participant added", service.addParticipant(meetingId, request));
    }

    @DeleteMapping("/{meetingId}/participants/{participantId}")
    @Operation(summary = "Remove a meeting participant",
            description = "Only the organizer or a current in-scope reviewer may remove a participant. The organizer participant cannot be removed.")
    public ApiResponse<Void> removeParticipant(@PathVariable UUID meetingId, @PathVariable UUID participantId) {
        service.removeParticipant(meetingId, participantId);
        return ApiResponse.success("Meeting participant removed", null);
    }
}
