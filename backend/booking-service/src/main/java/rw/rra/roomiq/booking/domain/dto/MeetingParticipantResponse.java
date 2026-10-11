package rw.rra.roomiq.booking.domain.dto;

import rw.rra.roomiq.booking.domain.enums.InviteStatus;
import rw.rra.roomiq.booking.domain.enums.ParticipantRole;

import java.util.UUID;

public record MeetingParticipantResponse(
        UUID participantId,
        UUID meetingId,
        UUID userId,
        String externalEmail,
        String displayName,
        ParticipantRole role,
        InviteStatus inviteStatus) {
}
