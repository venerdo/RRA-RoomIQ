package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.booking.domain.entity.ReservationOccurrence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationOccurrenceRepository extends JpaRepository<ReservationOccurrence, UUID> {
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM reservation_occurrence
                WHERE room_id = :roomId
                  AND occupied_period && tstzrange(:startsAt, :occupiedUntil, '[)')
            )
            """, nativeQuery = true)
    boolean existsRoomOccupancyConflict(@Param("roomId") UUID roomId,
                                        @Param("startsAt") Instant startsAt,
                                        @Param("occupiedUntil") Instant occupiedUntil);

    List<ReservationOccurrence> findAllByReservation_IdOrderByStartAt(UUID reservationId);

    @Query("select occurrence from ReservationOccurrence occurrence "
            + "where occurrence.reservation.bookingRequest.id = :requestId order by occurrence.startAt")
    List<ReservationOccurrence> findAllByBookingRequestId(@Param("requestId") UUID requestId);
}
