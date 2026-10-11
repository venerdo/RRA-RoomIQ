package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.booking.domain.entity.Cancellation;

import java.util.UUID;
import java.util.Optional;

public interface CancellationRepository extends JpaRepository<Cancellation, UUID> {
    Optional<Cancellation> findFirstByReservation_IdOrderByCancelledAtDesc(UUID reservationId);

    Optional<Cancellation> findFirstByBookingRequest_IdOrderByCancelledAtDesc(UUID bookingRequestId);
}
