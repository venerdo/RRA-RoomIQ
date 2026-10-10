package rw.rra.roomiq.booking.domain.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.booking.domain.dto.ReservationLifecycleResponse;
import rw.rra.roomiq.booking.domain.entity.Reservation;
import rw.rra.roomiq.booking.domain.repository.ReservationRepository;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ReservationLifecycleService {
    private final ReservationRepository reservations;
    private final BookingAuthorizationClient authorizationClient;
    private final Clock clock;

    @Autowired
    public ReservationLifecycleService(ReservationRepository reservations,
                                       BookingAuthorizationClient authorizationClient) {
        this(reservations, authorizationClient, Clock.systemUTC());
    }

    ReservationLifecycleService(ReservationRepository reservations,
                                BookingAuthorizationClient authorizationClient, Clock clock) {
        this.reservations = reservations;
        this.authorizationClient = authorizationClient;
        this.clock = clock;
    }

    @Transactional
    public ReservationLifecycleResponse checkIn(UUID reservationId) {
        Reservation reservation = lockReservation(reservationId);
        authorize(reservation);
        if (!reservation.checkIn(serverTimestamp())) {
            throw invalidTransition();
        }
        return response(reservations.saveAndFlush(reservation));
    }

    @Transactional
    public ReservationLifecycleResponse complete(UUID reservationId) {
        Reservation reservation = lockReservation(reservationId);
        authorize(reservation);
        if (!reservation.complete(serverTimestamp())) {
            throw invalidTransition();
        }
        return response(reservations.saveAndFlush(reservation));
    }

    private Reservation lockReservation(UUID reservationId) {
        if (reservationId == null) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "RESERVATION_ID_INVALID",
                    "A reservation identifier is required");
        }
        return reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                        "Reservation was not found"));
    }

    private void authorize(Reservation reservation) {
        UUID actor = authorizationClient.authorizeReservationLifecycle(reservation.getOrganizerUserId(),
                reservation.getBookingRequest().getDepartmentId(),
                reservation.getBookingRequest().getOfficeBuildingId());
        if (actor == null) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AUTHORIZATION_UNAVAILABLE",
                    "Identity authorization is unavailable; the reservation operation cannot proceed");
        }
    }

    private static ReservationLifecycleResponse response(Reservation reservation) {
        return new ReservationLifecycleResponse(reservation.getId(), reservation.getStatus(),
                reservation.getCheckedInAt(), reservation.getCompletedAt());
    }

    private Instant serverTimestamp() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static DomainException invalidTransition() {
        return new DomainException(HttpStatus.CONFLICT, "RESERVATION_STATE_CONFLICT",
                "The reservation is not in a state that permits this lifecycle transition");
    }
}
