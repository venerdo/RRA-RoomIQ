package rw.rra.roomiq.booking.domain.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.postgresql.util.PGobject;
import rw.rra.roomiq.booking.domain.dto.BookingDecisionRequest;
import rw.rra.roomiq.booking.domain.entity.ApprovalDecision;
import rw.rra.roomiq.booking.domain.entity.BookingRequest;
import rw.rra.roomiq.booking.domain.entity.Meeting;
import rw.rra.roomiq.booking.domain.entity.MeetingParticipant;
import rw.rra.roomiq.booking.domain.entity.Reservation;
import rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.BookingRequestType;
import rw.rra.roomiq.booking.domain.enums.InviteStatus;
import rw.rra.roomiq.booking.domain.enums.MeetingVisibility;
import rw.rra.roomiq.booking.domain.enums.ParticipantRole;
import rw.rra.roomiq.booking.domain.enums.ReservationStatus;
import rw.rra.roomiq.booking.domain.repository.ApprovalDecisionRepository;
import rw.rra.roomiq.booking.domain.repository.BookingRequestRepository;
import rw.rra.roomiq.booking.domain.repository.MeetingParticipantRepository;
import rw.rra.roomiq.booking.domain.repository.MeetingRepository;
import rw.rra.roomiq.booking.domain.repository.ReservationRepository;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.booking.integration.BookingAuthorizationResponse;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient.BookingRequestFacts;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient.ValidatedBookingReferences;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingDecisionServiceTests {
    private static final String TOKEN = "Bearer test-caller-token";
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Instant START = Instant.parse("2030-04-10T10:00:00Z");
    private static final Instant END = Instant.parse("2030-04-10T11:00:00Z");
    private static final String REQUESTER_NAME = "Trusted Requester";

    @Mock
    private BookingRequestRepository bookingRequests;
    @Mock
    private ApprovalDecisionRepository decisions;
    @Mock
    private ReservationRepository reservations;
    @Mock
    private MeetingRepository meetings;
    @Mock
    private MeetingParticipantRepository participants;
    @Mock
    private BookingAuthorizationClient authorizationClient;
    @Mock
    private BookingOwnerServicesClient ownerServicesClient;

    private BookingDecisionService service;
    private UUID requestId;
    private UUID requesterId;
    private UUID approverId;
    private UUID buildingId;
    private UUID departmentId;
    private UUID roomId;
    private BookingRequest pendingRequest;
    private ValidatedBookingReferences references;

    @BeforeEach
    void setUp() {
        service = new BookingDecisionService(bookingRequests, decisions, reservations, meetings, participants,
                authorizationClient, ownerServicesClient, Clock.fixed(NOW, ZoneOffset.UTC));
        requestId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        approverId = UUID.randomUUID();
        buildingId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        pendingRequest = new BookingRequest("BR-" + UUID.randomUUID(), BookingRequestType.SECRETARY_REQUEST,
                requesterId, departmentId, roomId, buildingId, null, "Planning session", "Review the roadmap",
                START, END, 5, false, BookingRequestStatus.PENDING_APPROVAL, "idem-" + UUID.randomUUID(),
                NOW.minusSeconds(60));
        references = new ValidatedBookingReferences(buildingId, departmentId, roomId,
                false, "Africa/Kigali", true, 15);
        when(bookingRequests.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));
        when(authorizationClient.authorizeApproval(requesterId, buildingId))
                .thenReturn(new BookingAuthorizationResponse(approverId, requesterId, REQUESTER_NAME));
        lenient().when(decisions.existsByBookingRequest_Id(requestId)).thenReturn(false);
        lenient().when(reservations.existsByBookingRequest_Id(requestId)).thenReturn(false);
        lenient().when(ownerServicesClient.validateRequest(any(BookingRequestFacts.class), eq(TOKEN), eq(NOW)))
                .thenReturn(references);
        lenient().when(reservations.existsRoomOccupancyConflict(roomId, START, END.plusSeconds(900)))
                .thenReturn(false);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("Authorization", TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void approvalRevalidatesAuthoritativeInputsAndCreatesOnePrivateMeetingAndConfirmedReservation() {
        var response = service.decide(requestId, new BookingDecisionRequest(ApprovalDecisionType.APPROVED,
                "  Reviewed by scoped administrator  "));

        assertThat(response.requestStatus()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(response.decision()).isEqualTo(ApprovalDecisionType.APPROVED);
        assertThat(response.decidedByUserId()).isEqualTo(approverId);
        assertThat(response.decidedAt()).isEqualTo(NOW);
        assertThat(response.comment()).isEqualTo("Reviewed by scoped administrator");
        assertThat(pendingRequest.getStatus()).isEqualTo(BookingRequestStatus.APPROVED);

        ArgumentCaptor<ApprovalDecision> decision = ArgumentCaptor.forClass(ApprovalDecision.class);
        verify(decisions).saveAndFlush(decision.capture());
        assertThat(decision.getValue().getDecision()).isEqualTo(ApprovalDecisionType.APPROVED);
        assertThat(decision.getValue().getDecidedByUserId()).isEqualTo(approverId);
        assertThat(decision.getValue().getDecidedAt()).isEqualTo(NOW);

        ArgumentCaptor<Reservation> reservation = ArgumentCaptor.forClass(Reservation.class);
        verify(reservations).saveAndFlush(reservation.capture());
        assertThat(reservation.getValue().getRoomId()).isEqualTo(roomId);
        assertThat(reservation.getValue().getOrganizerUserId()).isEqualTo(requesterId);
        assertThat(reservation.getValue().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getValue().getReleaseBufferMinutes()).isEqualTo(15);
        PGobject occupiedPeriod = reservation.getValue().getOccupiedPeriod();
        assertThat(occupiedPeriod.getType()).isEqualTo("tstzrange");
        assertThat(occupiedPeriod.getValue()).isEqualTo("[2030-04-10T10:00:00Z,2030-04-10T11:15:00Z)");

        ArgumentCaptor<Meeting> meeting = ArgumentCaptor.forClass(Meeting.class);
        verify(meetings).saveAndFlush(meeting.capture());
        assertThat(meeting.getValue().getTitle()).isEqualTo("Planning session");
        assertThat(meeting.getValue().getAgenda()).isEqualTo("Review the roadmap");
        assertThat(meeting.getValue().getOrganizerDisplayName()).isEqualTo(REQUESTER_NAME);
        assertThat(meeting.getValue().getVisibility()).isEqualTo(MeetingVisibility.PRIVATE);

        ArgumentCaptor<MeetingParticipant> participant = ArgumentCaptor.forClass(MeetingParticipant.class);
        verify(participants).saveAndFlush(participant.capture());
        assertThat(participant.getValue().getUserId()).isEqualTo(requesterId);
        assertThat(participant.getValue().getDisplayName()).isEqualTo(REQUESTER_NAME);
        assertThat(participant.getValue().getRole()).isEqualTo(ParticipantRole.ORGANIZER);
        assertThat(participant.getValue().getInviteStatus()).isEqualTo(InviteStatus.ACCEPTED);
        verify(ownerServicesClient).validateRequest(any(BookingRequestFacts.class), eq(TOKEN), eq(NOW));
    }

    @Test
    void rejectionPersistsTheDecisionAndRequestStateWithoutCreatingOccupancy() {
        var response = service.decide(requestId,
                new BookingDecisionRequest(ApprovalDecisionType.REJECTED, "Policy denied"));

        assertThat(response.requestStatus()).isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(response.reservationId()).isNull();
        assertThat(response.meetingId()).isNull();
        assertThat(pendingRequest.getStatus()).isEqualTo(BookingRequestStatus.REJECTED);
        verify(decisions).saveAndFlush(any(ApprovalDecision.class));
        verify(bookingRequests).saveAndFlush(pendingRequest);
        verifyNoInteractions(ownerServicesClient);
        verify(reservations, never()).saveAndFlush(any(Reservation.class));
        verifyNoInteractions(meetings, participants);
    }

    @Test
    void staleRequestCannotBeDecidedOrWriteHistory() {
        pendingRequest = new BookingRequest("BR-" + UUID.randomUUID(), BookingRequestType.SECRETARY_REQUEST,
                requesterId, departmentId, roomId, buildingId, null, "Planning session", null,
                START, END, 5, false, BookingRequestStatus.DRAFT, null, NOW.minusSeconds(60));
        when(bookingRequests.findByIdForUpdate(requestId)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> service.decide(requestId,
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    DomainException domainException = (DomainException) error;
                    assertThat(domainException.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(domainException.code()).isEqualTo("BOOKING_REQUEST_STATE_CONFLICT");
                });

        verify(decisions, never()).saveAndFlush(any());
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(ownerServicesClient, meetings, participants);
    }

    @Test
    void requesterCannotApproveTheirOwnRequest() {
        when(authorizationClient.authorizeApproval(requesterId, buildingId))
                .thenReturn(new BookingAuthorizationResponse(requesterId, requesterId, REQUESTER_NAME));

        assertThatThrownBy(() -> service.decide(requestId,
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).status()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(decisions, never()).saveAndFlush(any());
        verifyNoInteractions(ownerServicesClient, meetings, participants);
    }

    @Test
    void identityAuthorizationFailurePreventsAllOwnerLookupsAndWrites() {
        when(authorizationClient.authorizeApproval(requesterId, buildingId))
                .thenThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Denied"));

        assertThatThrownBy(() -> service.decide(requestId,
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).status()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(decisions, never()).saveAndFlush(any());
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(ownerServicesClient, meetings, participants);
        assertThat(pendingRequest.getStatus()).isEqualTo(BookingRequestStatus.PENDING_APPROVAL);
    }

    @Test
    void missingTrustedRequesterProfilePreventsApprovalWrites() {
        when(authorizationClient.authorizeApproval(requesterId, buildingId))
                .thenReturn(new BookingAuthorizationResponse(approverId, requesterId, null));

        assertThatThrownBy(() -> service.decide(requestId,
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).status())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        verify(decisions, never()).saveAndFlush(any());
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(ownerServicesClient, meetings, participants);
    }

    @Test
    void occupiedRoomFailsWithStableConflictWithoutWritingAnyDecision() {
        when(reservations.existsRoomOccupancyConflict(roomId, START, END.plusSeconds(900))).thenReturn(true);

        assertThatThrownBy(() -> service.decide(requestId,
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    DomainException domainException = (DomainException) error;
                    assertThat(domainException.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(domainException.code()).isEqualTo("ROOM_OCCUPANCY_CONFLICT");
                });

        verify(decisions, never()).saveAndFlush(any());
        verify(reservations, never()).saveAndFlush(any());
        verifyNoInteractions(meetings, participants);
        assertThat(pendingRequest.getStatus()).isEqualTo(BookingRequestStatus.PENDING_APPROVAL);
    }

    @Test
    void duplicateDecisionOrReservationPreventsOwnerCallsAndWrites() {
        when(decisions.existsByBookingRequest_Id(requestId)).thenReturn(true);

        assertThatThrownBy(() -> service.decide(requestId,
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    DomainException domainException = (DomainException) error;
                    assertThat(domainException.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(domainException.code()).isEqualTo("BOOKING_DECISION_CONFLICT");
                });

        verifyNoInteractions(ownerServicesClient, meetings, participants);
        verify(decisions, never()).saveAndFlush(any());
        verify(reservations, never()).saveAndFlush(any());
    }
}
