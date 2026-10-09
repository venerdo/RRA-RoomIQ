package rw.rra.roomiq.booking.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.booking.domain.entity.BookingRequest;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;

import java.util.Optional;
import java.util.UUID;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from BookingRequest request where request.id = :id")
    Optional<BookingRequest> findByIdForUpdate(@Param("id") UUID id);

    Optional<BookingRequest> findByIdempotencyKey(String idempotencyKey);

    Page<BookingRequest> findByRequestedByUserId(UUID requestedByUserId, Pageable pageable);

    Page<BookingRequest> findByRequestedByUserIdAndStatus(
            UUID requestedByUserId, BookingRequestStatus status, Pageable pageable);

    Page<BookingRequest> findByOfficeBuildingId(UUID officeBuildingId, Pageable pageable);

    Page<BookingRequest> findByOfficeBuildingIdAndStatus(
            UUID officeBuildingId, BookingRequestStatus status, Pageable pageable);
}
