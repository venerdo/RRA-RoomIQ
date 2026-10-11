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
                  AND occupancy_active = TRUE
                  AND occupied_period && tstzrange(:startsAt, :occupiedUntil, '[)')
            )
            """, nativeQuery = true)
    boolean existsRoomOccupancyConflict(@Param("roomId") UUID roomId,
                                        @Param("startsAt") Instant startsAt,
                                        @Param("occupiedUntil") Instant occupiedUntil);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM reservation_occurrence
                WHERE room_id = :roomId
                  AND reservation_id <> :reservationId
                  AND occupancy_active = TRUE
                  AND occupied_period && tstzrange(:startsAt, :occupiedUntil, '[)')
            )
            """, nativeQuery = true)
    boolean existsRoomOccupancyConflictExcludingReservation(@Param("roomId") UUID roomId,
                                                              @Param("reservationId") UUID reservationId,
                                                              @Param("startsAt") Instant startsAt,
                                                              @Param("occupiedUntil") Instant occupiedUntil);

    List<ReservationOccurrence> findAllByReservation_IdOrderByStartAt(UUID reservationId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select occurrence from ReservationOccurrence occurrence
            where occurrence.reservation.id = :reservationId
            order by occurrence.startAt
            """)
    List<ReservationOccurrence> findAllByReservationIdForUpdate(
            @org.springframework.data.repository.query.Param("reservationId") UUID reservationId);

    @Query("select occurrence from ReservationOccurrence occurrence "
            + "where occurrence.reservation.bookingRequest.id = :requestId order by occurrence.startAt")
    List<ReservationOccurrence> findAllByBookingRequestId(@Param("requestId") UUID requestId);
}
