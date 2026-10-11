package rw.rra.roomiq.booking.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.booking.domain.dto.AddMeetingParticipantRequest;
import rw.rra.roomiq.booking.domain.dto.MeetingParticipantResponse;
import rw.rra.roomiq.booking.domain.dto.MeetingResponse;
import rw.rra.roomiq.booking.domain.entity.Meeting;
import rw.rra.roomiq.booking.domain.entity.MeetingParticipant;
import rw.rra.roomiq.booking.domain.enums.InviteStatus;
import rw.rra.roomiq.booking.domain.enums.ParticipantRole;
import rw.rra.roomiq.booking.domain.repository.MeetingParticipantRepository;
import rw.rra.roomiq.booking.domain.repository.MeetingRepository;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.booking.integration.MeetingAuthorizationRequest;
import rw.rra.roomiq.booking.integration.MeetingAuthorizationRequest.Action;
import rw.rra.roomiq.booking.integration.MeetingAuthorizationResponse;
import rw.rra.roomiq.common.web.DomainException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class MeetingAccessService {
    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final BookingAuthorizationClient authorization;

    public MeetingAccessService(MeetingRepository meetings, MeetingParticipantRepository participants,
                                BookingAuthorizationClient authorization) {
        this.meetings = meetings;
        this.participants = participants;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID meetingId) {
        Meeting meeting = getRequired(meetingId);
        MeetingAuthorizationResponse decision = authorize(meeting, Action.READ, null);
        return meetingResponse(meeting, decision);
    }

    @Transactional(readOnly = true)
    public List<MeetingParticipantResponse> listParticipants(UUID meetingId) {
        Meeting meeting = getRequired(meetingId);
        MeetingAuthorizationResponse decision = authorize(meeting, Action.READ, null);
        return participants.findAllByMeeting_IdOrderById(meetingId).stream()
                .map(participant -> participantResponse(meeting, participant, decision))
                .toList();
    }

    @Transactional
    public MeetingParticipantResponse addParticipant(UUID meetingId, AddMeetingParticipantRequest request) {
        Meeting meeting = getForUpdate(meetingId);
        UUID targetUserId = request.userId();
        MeetingAuthorizationResponse identity = authorize(meeting, Action.MANAGE, targetUserId);
        if (targetUserId == null
                && !Boolean.TRUE.equals(meeting.getReservation().getBookingRequest().getExternalGuests())) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "EXTERNAL_GUESTS_NOT_ALLOWED",
                    "The approved booking request does not allow external guests");
        }
        if (targetUserId != null && participants.existsByMeeting_IdAndUserId(meetingId, targetUserId)) {
            throw conflict("MEETING_PARTICIPANT_EXISTS", "The internal participant is already on this meeting");
        }
        String email = normalizeEmail(request.externalEmail());
        if (email != null && participants.existsByMeeting_IdAndExternalEmailIgnoreCase(meetingId, email)) {
            throw conflict("MEETING_PARTICIPANT_EXISTS", "The external participant is already on this meeting");
        }
        MeetingParticipant participant = new MeetingParticipant(meeting, targetUserId, email,
                targetUserId == null ? request.displayName().trim() : identity.participantDisplayName(),
                request.role(), InviteStatus.INVITED);
        return participantResponse(meeting, participants.saveAndFlush(participant), identity);
    }

    @Transactional
    public void removeParticipant(UUID meetingId, UUID participantId) {
        Meeting meeting = getForUpdate(meetingId);
        authorize(meeting, Action.MANAGE, null);
        MeetingParticipant participant = participants.findByIdAndMeeting_Id(participantId, meetingId)
                .orElseThrow(() -> notFound("Meeting participant was not found"));
        if (participant.getRole() == ParticipantRole.ORGANIZER) {
            throw conflict("MEETING_ORGANIZER_IMMUTABLE", "The meeting organizer participant cannot be removed");
        }
        participants.delete(participant);
    }

    private MeetingAuthorizationResponse authorize(Meeting meeting, Action action, UUID participantUserId) {
        var reservation = meeting.getReservation();
        var bookingRequest = reservation.getBookingRequest();
        if (!Objects.equals(reservation.getOrganizerUserId(), bookingRequest.getRequestedByUserId())
                || !Objects.equals(reservation.getRoomId(), bookingRequest.getRoomId())
                || bookingRequest.getOfficeBuildingId() == null || bookingRequest.getDepartmentId() == null) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "MEETING_FACTS_INCONSISTENT",
                    "Persisted meeting ownership or scope is inconsistent; access cannot be evaluated");
        }
        return authorization.authorizeMeeting(new MeetingAuthorizationRequest(action,
                bookingRequest.getOfficeBuildingId(), bookingRequest.getDepartmentId(),
                reservation.getOrganizerUserId(), meeting.getVisibility(), participantUserId));
    }

    private Meeting getRequired(UUID meetingId) {
        return meetings.findById(meetingId)
                .orElseThrow(() -> notFound("Meeting was not found"));
    }

    private Meeting getForUpdate(UUID meetingId) {
        return meetings.findByIdForUpdate(meetingId)
                .orElseThrow(() -> notFound("Meeting was not found"));
    }

    private static MeetingResponse meetingResponse(Meeting meeting, MeetingAuthorizationResponse decision) {
        var reservation = meeting.getReservation();
        var bookingRequest = reservation.getBookingRequest();
        String organizerDisplayName = decision.organizerDisplayName() == null
                || decision.organizerDisplayName().isBlank()
                ? meeting.getOrganizerDisplayName() : decision.organizerDisplayName();
        return new MeetingResponse(meeting.getId(), reservation.getId(), reservation.getOrganizerUserId(),
                reservation.getRoomId(), bookingRequest.getOfficeBuildingId(), bookingRequest.getDepartmentId(),
                reservation.getStartAt(), reservation.getEndAt(), reservation.getStatus(), meeting.getTitle(),
                meeting.getAgenda(), organizerDisplayName, meeting.getInstructions(), meeting.getContactInfo(),
                meeting.getVisibility(), meeting.getCreatedAt());
    }

    private static MeetingParticipantResponse participantResponse(
            Meeting meeting, MeetingParticipant participant, MeetingAuthorizationResponse decision) {
        UUID organizerUserId = meeting.getReservation().getOrganizerUserId();
        if (participant.getUserId() == null && participant.getExternalEmail() == null
                || participant.getUserId() != null && participant.getExternalEmail() != null
                || participant.getRole() == ParticipantRole.ORGANIZER
                && (!organizerUserId.equals(participant.getUserId()) || participant.getExternalEmail() != null)) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "MEETING_PARTICIPANT_DATA_INCONSISTENT",
                    "Persisted meeting participant identity is inconsistent");
        }
        String displayName = participant.getRole() == ParticipantRole.ORGANIZER
                && decision.organizerDisplayName() != null && !decision.organizerDisplayName().isBlank()
                ? decision.organizerDisplayName() : participant.getDisplayName();
        return new MeetingParticipantResponse(participant.getId(), participant.getMeeting().getId(),
                participant.getUserId(), participant.getExternalEmail(), displayName,
                participant.getRole(), participant.getInviteStatus());
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static DomainException notFound(String message) {
        return new DomainException(HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND", message);
    }

    private static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }
}
