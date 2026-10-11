package rw.rra.roomiq.booking.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import rw.rra.roomiq.booking.domain.dto.AddMeetingParticipantRequest;
import rw.rra.roomiq.booking.domain.entity.BookingRequest;
import rw.rra.roomiq.booking.domain.entity.Meeting;
import rw.rra.roomiq.booking.domain.entity.MeetingParticipant;
import rw.rra.roomiq.booking.domain.entity.Reservation;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.BookingRequestType;
import rw.rra.roomiq.booking.domain.enums.InviteStatus;
import rw.rra.roomiq.booking.domain.enums.MeetingVisibility;
import rw.rra.roomiq.booking.domain.enums.ParticipantRole;
import rw.rra.roomiq.booking.domain.enums.ReservationStatus;
import rw.rra.roomiq.booking.domain.repository.MeetingParticipantRepository;
import rw.rra.roomiq.booking.domain.repository.MeetingRepository;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.booking.integration.MeetingAuthorizationRequest;
import rw.rra.roomiq.booking.integration.MeetingAuthorizationResponse;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingAccessServiceTests {
    private static final Instant START = Instant.parse("2030-04-10T10:00:00Z");
    private static final Instant END = Instant.parse("2030-04-10T11:00:00Z");

    @Mock
    private MeetingRepository meetings;

    @Mock
    private MeetingParticipantRepository participants;

    @Mock
    private BookingAuthorizationClient authorization;

    private MeetingAccessService service;
    private UUID meetingId;
    private UUID organizerId;
    private UUID buildingId;
    private UUID departmentId;
    private UUID roomId;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        service = new MeetingAccessService(meetings, participants, authorization);
        meetingId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        buildingId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        BookingRequest request = new BookingRequest("BR-" + UUID.randomUUID(),
                BookingRequestType.ADMIN_DIRECT_BOOKING, organizerId, departmentId, roomId, buildingId,
                null, "Planning session", "Planning", START, END, 4, false,
                BookingRequestStatus.APPROVED, "idem-" + UUID.randomUUID(), START.minusSeconds(60));
        Reservation reservation = new Reservation(request, roomId, organizerId, null, null, START, END, 5,
                ReservationStatus.CONFIRMED, START);
        meeting = new Meeting(reservation, "Planning session", "Planning", "Stored Organizer",
                "Instructions", "organizer@example.test", MeetingVisibility.PRIVATE, START);
        ReflectionTestUtils.setField(meeting, "id", meetingId);

        lenient().when(authorization.authorizeMeeting(any(MeetingAuthorizationRequest.class)))
                .thenAnswer(invocation -> {
                    MeetingAuthorizationRequest facts = invocation.getArgument(0);
                    return new MeetingAuthorizationResponse(UUID.randomUUID(), facts.buildingId(),
                            facts.departmentId(), facts.organizerUserId(), facts.visibility(), "Identity Organizer",
                            facts.participantUserId(), facts.participantUserId() == null
                            ? null : "Identity Participant");
                });
    }

    @Test
    void meetingReadUsesPersistedFactsAndIdentityDerivedOrganizerDisplayName() {
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting));

        var result = service.getMeeting(meetingId);

        assertThat(result.organizerDisplayName()).isEqualTo("Identity Organizer");
        assertThat(result.organizerUserId()).isEqualTo(organizerId);
        assertThat(result.buildingId()).isEqualTo(buildingId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.instructions()).isEqualTo("Instructions");
        ArgumentCaptor<MeetingAuthorizationRequest> facts =
                ArgumentCaptor.forClass(MeetingAuthorizationRequest.class);
        verify(authorization).authorizeMeeting(facts.capture());
        assertThat(facts.getValue().action()).isEqualTo(MeetingAuthorizationRequest.Action.READ);
        assertThat(facts.getValue().organizerUserId()).isEqualTo(organizerId);
        assertThat(facts.getValue().visibility()).isEqualTo(MeetingVisibility.PRIVATE);
    }

    @Test
    void organizerParticipantDisplayNameIsRefreshedFromIdentity() {
        UUID participantId = UUID.randomUUID();
        MeetingParticipant organizerParticipant = new MeetingParticipant(meeting, organizerId, null,
                "Old Snapshot", ParticipantRole.ORGANIZER, InviteStatus.ACCEPTED);
        ReflectionTestUtils.setField(organizerParticipant, "id", participantId);
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(participants.findAllByMeeting_IdOrderById(meetingId)).thenReturn(
                java.util.List.of(organizerParticipant));

        var result = service.listParticipants(meetingId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).displayName()).isEqualTo("Identity Organizer");
    }

    @Test
    void privateParticipantListDenialOccursBeforeParticipantDetailsAreLoaded() {
        when(meetings.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(authorization.authorizeMeeting(any(MeetingAuthorizationRequest.class)))
                .thenThrow(new DomainException(HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED",
                        "Meeting access is not authorized"));

        assertThatThrownBy(() -> service.listParticipants(meetingId))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).status())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(participants);
    }

    @Test
    void unauthorizedParticipantManagementFailsBeforeAnyParticipantWrite() {
        when(meetings.findByIdForUpdate(meetingId)).thenReturn(Optional.of(meeting));
        when(authorization.authorizeMeeting(any(MeetingAuthorizationRequest.class)))
                .thenThrow(new DomainException(HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED",
                        "Meeting access is not authorized"));

        assertThatThrownBy(() -> service.addParticipant(meetingId,
                new AddMeetingParticipantRequest(null, "guest@example.test", "Guest",
                        ParticipantRole.REQUIRED)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).status())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(participants);
    }

    @Test
    void addInternalParticipantUsesIdentityNameAndServerControlledInvitationState() {
        UUID participantId = UUID.randomUUID();
        when(meetings.findByIdForUpdate(meetingId)).thenReturn(Optional.of(meeting));
        when(participants.existsByMeeting_IdAndUserId(meetingId, participantId)).thenReturn(false);
        when(participants.saveAndFlush(any(MeetingParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.addParticipant(meetingId,
                new AddMeetingParticipantRequest(participantId, null, null, ParticipantRole.REQUIRED));

        assertThat(result.userId()).isEqualTo(participantId);
        assertThat(result.displayName()).isEqualTo("Identity Participant");
        assertThat(result.inviteStatus()).isEqualTo(InviteStatus.INVITED);
        ArgumentCaptor<MeetingParticipant> saved = ArgumentCaptor.forClass(MeetingParticipant.class);
        verify(participants).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getExternalEmail()).isNull();
        assertThat(saved.getValue().getRole()).isEqualTo(ParticipantRole.REQUIRED);
    }

    @Test
    void addExternalParticipantNormalizesEmailAndDoesNotTrustCallerForIdentityFields() {
        when(meetings.findByIdForUpdate(meetingId)).thenReturn(Optional.of(meeting));
        ReflectionTestUtils.setField(meeting.getReservation().getBookingRequest(), "externalGuests", true);
        when(participants.existsByMeeting_IdAndExternalEmailIgnoreCase(meetingId, "guest@example.test"))
                .thenReturn(false);
        when(participants.saveAndFlush(any(MeetingParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.addParticipant(meetingId, new AddMeetingParticipantRequest(
                null, " Guest@Example.Test ", "External Guest", ParticipantRole.OPTIONAL));

        assertThat(result.externalEmail()).isEqualTo("guest@example.test");
        assertThat(result.displayName()).isEqualTo("External Guest");
        assertThat(result.inviteStatus()).isEqualTo(InviteStatus.INVITED);
        verify(authorization).authorizeMeeting(any(MeetingAuthorizationRequest.class));
    }

    @Test
    void externalParticipantIsRejectedWhenApprovedBookingDisallowsExternalGuests() {
        when(meetings.findByIdForUpdate(meetingId)).thenReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.addParticipant(meetingId, new AddMeetingParticipantRequest(
                null, "guest@example.test", "External Guest", ParticipantRole.OPTIONAL)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> {
                    var domainException = (DomainException) exception;
                    assertThat(domainException.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(domainException.code()).isEqualTo("EXTERNAL_GUESTS_NOT_ALLOWED");
                });
        verify(authorization).authorizeMeeting(any(MeetingAuthorizationRequest.class));
        verifyNoInteractions(participants);
    }

    @Test
    void duplicateParticipantAndOrganizerMutationAreRejected() {
        UUID participantId = UUID.randomUUID();
        when(meetings.findByIdForUpdate(meetingId)).thenReturn(Optional.of(meeting));
        when(participants.existsByMeeting_IdAndUserId(meetingId, participantId)).thenReturn(true);

        assertThatThrownBy(() -> service.addParticipant(meetingId,
                new AddMeetingParticipantRequest(participantId, null, null, ParticipantRole.REQUIRED)))
                .isInstanceOf(DomainException.class);

        MeetingParticipant organizerParticipant = org.mockito.Mockito.mock(MeetingParticipant.class);
        when(participants.findByIdAndMeeting_Id(any(UUID.class), eq(meetingId)))
                .thenReturn(Optional.of(organizerParticipant));
        when(organizerParticipant.getRole()).thenReturn(ParticipantRole.ORGANIZER);

        assertThatThrownBy(() -> service.removeParticipant(meetingId, UUID.randomUUID()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsInconsistentPersistedMeetingOwnershipBeforeCallingIdentity() {
        Reservation inconsistentReservation = org.mockito.Mockito.mock(Reservation.class);
        when(inconsistentReservation.getBookingRequest()).thenReturn(meeting.getReservation().getBookingRequest());
        when(inconsistentReservation.getOrganizerUserId()).thenReturn(UUID.randomUUID());
        Meeting inconsistentMeeting = org.mockito.Mockito.mock(Meeting.class);
        when(inconsistentMeeting.getReservation()).thenReturn(inconsistentReservation);
        when(meetings.findById(meetingId)).thenReturn(Optional.of(inconsistentMeeting));

        assertThatThrownBy(() -> service.getMeeting(meetingId))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).code())
                        .isEqualTo("MEETING_FACTS_INCONSISTENT"));
    }

    @Test
    void participantInputCannotImpersonateOrganizerOrSupplyInternalDisplayName() {
        assertThat(new AddMeetingParticipantRequest(UUID.randomUUID(), null, "Spoof",
                ParticipantRole.REQUIRED).isParticipantInputValid()).isFalse();
        assertThat(new AddMeetingParticipantRequest(UUID.randomUUID(), null, null,
                ParticipantRole.ORGANIZER).isParticipantInputValid()).isFalse();
        assertThat(new AddMeetingParticipantRequest(null, "guest@example.test", "Guest",
                ParticipantRole.REQUIRED).isParticipantInputValid()).isTrue();
    }
}
