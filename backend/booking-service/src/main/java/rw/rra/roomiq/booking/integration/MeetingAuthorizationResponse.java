package rw.rra.roomiq.booking.integration;

import rw.rra.roomiq.booking.domain.enums.MeetingVisibility;

import java.util.UUID;

public record MeetingAuthorizationResponse(
        UUID actorUserId,
        UUID buildingId,
        UUID departmentId,
        UUID organizerUserId,
        MeetingVisibility visibility,
        String organizerDisplayName,
        UUID participantUserId,
        String participantDisplayName) {
}
