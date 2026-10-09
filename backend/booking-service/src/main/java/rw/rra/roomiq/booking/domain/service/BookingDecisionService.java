package rw.rra.roomiq.booking.domain.service;

import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.booking.domain.dto.BookingDecisionRequest;
import rw.rra.roomiq.booking.domain.dto.BookingDecisionResponse;
import rw.rra.roomiq.booking.domain.entity.ApprovalDecision;
import rw.rra.roomiq.booking.domain.entity.BookingRequest;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.repository.ApprovalDecisionRepository;
import rw.rra.roomiq.booking.domain.repository.BookingRequestRepository;
import rw.rra.roomiq.booking.domain.repository.ReservationRepository;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.booking.integration.BookingAuthorizationResponse;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType.APPROVED;
import static rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType.REJECTED;
@Service
public class BookingDecisionService {
    private static final String UNIQUE_DECISION_CONSTRAINT = "uq_approval_decision_booking_request";
    private static final String UNIQUE_RESERVATION_CONSTRAINT = "uq_reservation_booking_request";

    private final BookingRequestRepository bookingRequests;
    private final ApprovalDecisionRepository decisions;
    private final ReservationRepository reservations;
    private final BookingAuthorizationClient authorizationClient;
    private final BookingConfirmationService confirmationService;
    private final Clock clock;

    @Autowired
    public BookingDecisionService(BookingRequestRepository bookingRequests,
                                  ApprovalDecisionRepository decisions,
                                  ReservationRepository reservations,
                                  BookingAuthorizationClient authorizationClient,
                                  BookingConfirmationService confirmationService) {
        this(bookingRequests, decisions, reservations, authorizationClient, confirmationService,
                Clock.systemUTC());
    }

    BookingDecisionService(BookingRequestRepository bookingRequests,
                           ApprovalDecisionRepository decisions,
                           ReservationRepository reservations,
                           BookingAuthorizationClient authorizationClient,
                           BookingConfirmationService confirmationService,
                           Clock clock) {
        this.bookingRequests = bookingRequests;
        this.decisions = decisions;
        this.reservations = reservations;
        this.authorizationClient = authorizationClient;
        this.confirmationService = confirmationService;
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
        BookingConfirmationService.ConfirmedBooking confirmed;
        try {
            decisions.saveAndFlush(decision);
            confirmed = confirmationService.confirm(bookingRequest, authorization, decidedAt);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, UNIQUE_DECISION_CONSTRAINT)
                    || hasConstraint(exception, UNIQUE_RESERVATION_CONSTRAINT)) {
                throw decisionConflict();
            }
            throw exception;
        }

        return new BookingDecisionResponse(bookingRequest.getId(), bookingRequest.getStatus(),
                APPROVED, actor, decidedAt, decision.getComment(),
                confirmed.reservation().getId(), confirmed.meeting().getId());
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

}
