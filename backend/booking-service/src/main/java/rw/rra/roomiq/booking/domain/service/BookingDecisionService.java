package rw.rra.roomiq.booking.domain.service;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PGobject;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.booking.domain.dto.BookingDecisionRequest;
import rw.rra.roomiq.booking.domain.dto.BookingDecisionResponse;
import rw.rra.roomiq.booking.domain.entity.ApprovalDecision;
import rw.rra.roomiq.booking.domain.entity.BookingRequest;
import rw.rra.roomiq.booking.domain.entity.Meeting;
import rw.rra.roomiq.booking.domain.entity.MeetingParticipant;
import rw.rra.roomiq.booking.domain.entity.Reservation;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
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

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType.APPROVED;
import static rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType.REJECTED;
@Service
public class BookingDecisionService {
    private static final String RESERVATION_OVERLAP_CONSTRAINT = "ex_reservation_room_occupied_period";
    private static final String UNIQUE_DECISION_CONSTRAINT = "uq_approval_decision_booking_request";
    private static final String UNIQUE_RESERVATION_CONSTRAINT = "uq_reservation_booking_request";

    private final BookingRequestRepository bookingRequests;
    private final ApprovalDecisionRepository decisions;
    private final ReservationRepository reservations;
    private final MeetingRepository meetings;
    private final MeetingParticipantRepository participants;
    private final BookingAuthorizationClient authorizationClient;
    private final BookingOwnerServicesClient ownerServicesClient;
    private final Clock clock;

    @Autowired
    public BookingDecisionService(BookingRequestRepository bookingRequests,
                                  ApprovalDecisionRepository decisions,
                                  ReservationRepository reservations,
                                  MeetingRepository meetings,
                                  MeetingParticipantRepository participants,
                                  BookingAuthorizationClient authorizationClient,
                                  BookingOwnerServicesClient ownerServicesClient) {
        this(bookingRequests, decisions, reservations, meetings, participants, authorizationClient,
                ownerServicesClient, Clock.systemUTC());
    }

    BookingDecisionService(BookingRequestRepository bookingRequests,
                           ApprovalDecisionRepository decisions,
                           ReservationRepository reservations,
                           MeetingRepository meetings,
                           MeetingParticipantRepository participants,
                           BookingAuthorizationClient authorizationClient,
                           BookingOwnerServicesClient ownerServicesClient,
                           Clock clock) {
        this.bookingRequests = bookingRequests;
        this.decisions = decisions;
        this.reservations = reservations;
        this.meetings = meetings;
        this.participants = participants;
        this.authorizationClient = authorizationClient;
        this.ownerServicesClient = ownerServicesClient;
        this.clock = clock;
    }

    @Transactional
    public BookingDecisionResponse decide(UUID bookingRequestId, BookingDecisionRequest request) {
        if (bookingRequestId == null || request == null || request.decision() == null) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "BOOKING_DECISION_INVALID",
                    "A booking request and decision are required");
        }

        BookingRequest bookingRequest = bookingRequests.findByIdForUpdate(bookingRequestId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "BOOKING_REQUEST_NOT_FOUND",
                        "Booking request was not found"));
        BookingAuthorizationResponse authorization = authorizationClient.authorizeApproval(
                bookingRequest.getRequestedByUserId(), bookingRequest.getOfficeBuildingId());
        UUID actor = authorization.actorUserId();
        if (actor.equals(bookingRequest.getRequestedByUserId())) {
            throw new DomainException(HttpStatus.FORBIDDEN, "BOOKING_SELF_APPROVAL_DENIED",
                    "A requester cannot decide their own booking request");
        }
        if (bookingRequest.getStatus() != BookingRequestStatus.PENDING_APPROVAL) {
            throw staleDecision();
        }
        if (decisions.existsByBookingRequest_Id(bookingRequestId)
                || reservations.existsByBookingRequest_Id(bookingRequestId)) {
            throw new DomainException(HttpStatus.CONFLICT, "BOOKING_DECISION_CONFLICT",
                    "A decision or reservation already exists for this booking request");
        }

        Instant decidedAt = clock.instant();
        String comment = trimToNull(request.comment());
        ApprovalDecision decision = new ApprovalDecision(bookingRequest, actor, request.decision(),
                comment, decidedAt);

        if (request.decision() == REJECTED) {
            if (!bookingRequest.reject()) {
                throw staleDecision();
            }
            return persistRejection(bookingRequest, decision, actor, decidedAt);
        }
        return approve(bookingRequest, decision, authorization, actor, decidedAt);
    }

    private BookingDecisionResponse persistRejection(BookingRequest bookingRequest, ApprovalDecision decision,
                                                      UUID actor, Instant decidedAt) {
        try {
            decisions.saveAndFlush(decision);
            bookingRequests.saveAndFlush(bookingRequest);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, UNIQUE_DECISION_CONSTRAINT)) {
                throw decisionConflict();
            }
            throw exception;
        }
        return new BookingDecisionResponse(bookingRequest.getId(), bookingRequest.getStatus(),
                REJECTED, actor, decidedAt, decision.getComment(), null, null);
    }

    private BookingDecisionResponse approve(BookingRequest bookingRequest, ApprovalDecision decision,
                                            BookingAuthorizationResponse authorization, UUID actor,
                                            Instant decidedAt) {
        UUID requesterId = bookingRequest.getRequestedByUserId();
        if (!requesterId.equals(authorization.resourceOwnerUserId())
                || authorization.resourceOwnerDisplayName() == null
                || authorization.resourceOwnerDisplayName().isBlank()) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_CONTENT, "BOOKING_REQUESTER_IDENTITY_INVALID",
                    "The current requester identity is unavailable or inconsistent");
        }

        ValidatedBookingReferences references = ownerServicesClient.validateRequest(
                new BookingRequestFacts(bookingRequest.getDepartmentId(), bookingRequest.getRoomId(),
                        bookingRequest.getOfficeBuildingId(), bookingRequest.getRecurrenceRuleId(),
                        bookingRequest.getRequestedStart(), bookingRequest.getRequestedEnd(),
                        bookingRequest.getAttendeeCount(), bookingRequest.getExternalGuests()),
                currentBearerToken(), decidedAt);
        if (!bookingRequest.getOfficeBuildingId().equals(references.officeBuildingId())
                || !bookingRequest.getDepartmentId().equals(references.departmentId())
                || !bookingRequest.getRoomId().equals(references.roomId())) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_OWNER_DATA_INCONSISTENT",
                    "Current owner-service data does not match the stored booking request");
        }
        int releaseBufferMinutes = references.releaseBufferMinutes();
        if (releaseBufferMinutes < 0) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_BOOKING_POLICY_UNAVAILABLE",
                    "The current room release buffer is invalid");
        }
        Instant occupiedUntil = bookingRequest.getRequestedEnd()
                .plusSeconds(Math.multiplyExact((long) releaseBufferMinutes, 60));
        if (reservations.existsRoomOccupancyConflict(bookingRequest.getRoomId(),
                bookingRequest.getRequestedStart(), occupiedUntil)) {
            throw occupancyConflict();
        }

        if (!bookingRequest.approve()) {
            throw staleDecision();
        }
        Reservation reservation = new Reservation(bookingRequest, bookingRequest.getRoomId(), requesterId,
                bookingRequest.getRecurrenceRuleId(),
                occupiedPeriod(bookingRequest.getRequestedStart(), occupiedUntil),
                bookingRequest.getRequestedStart(), bookingRequest.getRequestedEnd(), releaseBufferMinutes,
                ReservationStatus.CONFIRMED, decidedAt);
        Meeting meeting = new Meeting(reservation, bookingRequest.getTitle(), bookingRequest.getPurpose(),
                authorization.resourceOwnerDisplayName(), null, null, MeetingVisibility.PRIVATE, decidedAt);
        MeetingParticipant organizer = new MeetingParticipant(meeting, requesterId, null,
                authorization.resourceOwnerDisplayName(), ParticipantRole.ORGANIZER, InviteStatus.ACCEPTED);

        try {
            decisions.saveAndFlush(decision);
            reservations.saveAndFlush(reservation);
            meetings.saveAndFlush(meeting);
            participants.saveAndFlush(organizer);
            bookingRequests.saveAndFlush(bookingRequest);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, RESERVATION_OVERLAP_CONSTRAINT)) {
                throw occupancyConflict();
            }
            if (hasConstraint(exception, UNIQUE_DECISION_CONSTRAINT)
                    || hasConstraint(exception, UNIQUE_RESERVATION_CONSTRAINT)) {
                throw decisionConflict();
            }
            throw exception;
        }

        return new BookingDecisionResponse(bookingRequest.getId(), bookingRequest.getStatus(),
                APPROVED, actor, decidedAt, decision.getComment(), reservation.getId(), meeting.getId());
    }

    private static PGobject occupiedPeriod(Instant startsAt, Instant occupiedUntil) {
        PGobject range = new PGobject();
        range.setType("tstzrange");
        try {
            range.setValue("[" + startsAt + "," + occupiedUntil + ")");
        } catch (SQLException exception) {
            throw new IllegalStateException("Validated booking interval could not be represented as tstzrange",
                    exception);
        }
        return range;
    }

    private static String currentBearerToken() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                    && !authorization.substring(7).isBlank()) {
                return authorization;
            }
        }
        throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required");
    }

    private static boolean hasConstraint(DataIntegrityViolationException exception, String expectedName) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && expectedName.equals(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof PSQLException postgresException
                    && postgresException.getServerErrorMessage() != null
                    && expectedName.equals(postgresException.getServerErrorMessage().getConstraint())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static DomainException staleDecision() {
        return new DomainException(HttpStatus.CONFLICT, "BOOKING_REQUEST_STATE_CONFLICT",
                "Only a pending booking request can be decided");
    }

    private static DomainException decisionConflict() {
        return new DomainException(HttpStatus.CONFLICT, "BOOKING_DECISION_CONFLICT",
                "A decision or reservation already exists for this booking request");
    }

    private static DomainException occupancyConflict() {
        return new DomainException(HttpStatus.CONFLICT, "ROOM_OCCUPANCY_CONFLICT",
                "The room is already occupied during the requested interval");
    }
}
