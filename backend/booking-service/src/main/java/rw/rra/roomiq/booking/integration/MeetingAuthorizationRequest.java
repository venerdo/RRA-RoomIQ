package rw.rra.roomiq.booking.integration;

import rw.rra.roomiq.booking.domain.enums.MeetingVisibility;

import java.util.UUID;

public record MeetingAuthorizationRequest(
        Action action,
        UUID buildingId,
        UUID departmentId,
        UUID organizerUserId,
        MeetingVisibility visibility,
        UUID participantUserId) {

    public enum Action {
        READ,
        MANAGE
    }
}
