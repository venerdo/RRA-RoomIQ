package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import rw.rra.roomiq.booking.domain.entity.BookingExtension;
import rw.rra.roomiq.booking.domain.enums.BookingExtensionStatus;

import java.util.Optional;
import java.util.UUID;

public interface BookingExtensionRepository extends JpaRepository<BookingExtension, UUID> {
    Optional<BookingExtension> findByIdempotencyKey(String idempotencyKey);

    boolean existsByReservation_IdAndStatus(UUID reservationId, BookingExtensionStatus status);

    @Query("select extension.reservation.id from BookingExtension extension where extension.id = :id")
    Optional<UUID> findReservationIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select extension from BookingExtension extension where extension.id = :id")
    Optional<BookingExtension> findByIdForUpdate(@Param("id") UUID id);
}
