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

    @Query(value = """
            SELECT recipients.recipient_user_id
            FROM (
                SELECT reservation.organizer_user_id AS recipient_user_id
                FROM reservation_occurrence occurrence
                JOIN reservation ON reservation.id = occurrence.reservation_id
                JOIN booking_request request ON request.id = reservation.booking_request_id
                WHERE occurrence.room_id = :roomId
                  AND request.office_building_id = :officeBuildingId
                  AND occurrence.occupancy_active = TRUE
                  AND reservation.status IN ('CONFIRMED', 'IN_PROGRESS')
                  AND occurrence.occupied_period
                      && tstzrange(:startsAt, CAST(:endsAt AS timestamptz), '[)')
                UNION
                SELECT participant.user_id AS recipient_user_id
                FROM reservation_occurrence occurrence
                JOIN reservation ON reservation.id = occurrence.reservation_id
                JOIN booking_request request ON request.id = reservation.booking_request_id
                JOIN meeting ON meeting.reservation_id = reservation.id
                JOIN meeting_participant participant ON participant.meeting_id = meeting.id
                WHERE occurrence.room_id = :roomId
                  AND request.office_building_id = :officeBuildingId
                  AND occurrence.occupancy_active = TRUE
                  AND reservation.status IN ('CONFIRMED', 'IN_PROGRESS')
                  AND participant.user_id IS NOT NULL
                  AND occurrence.occupied_period
                      && tstzrange(:startsAt, CAST(:endsAt AS timestamptz), '[)')
            ) recipients
            WHERE recipients.recipient_user_id IS NOT NULL
            ORDER BY recipients.recipient_user_id
            """, nativeQuery = true)
    List<UUID> findActiveInternalRecipientsForRoomEvent(
            @Param("roomId") UUID roomId,
            @Param("officeBuildingId") UUID officeBuildingId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);

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
