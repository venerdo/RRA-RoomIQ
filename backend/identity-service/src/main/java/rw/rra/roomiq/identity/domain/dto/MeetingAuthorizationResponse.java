package rw.rra.roomiq.identity.domain.dto;

import java.util.UUID;

public record MeetingAuthorizationResponse(
        UUID actorUserId,
        UUID buildingId,
        UUID departmentId,
        UUID organizerUserId,
        MeetingAuthorizationRequest.Visibility visibility,
        String organizerDisplayName,
        UUID participantUserId,
        String participantDisplayName) {
}
