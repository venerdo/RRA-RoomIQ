package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.booking.domain.entity.Reservation;

import java.time.Instant;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    boolean existsByBookingRequest_Id(UUID bookingRequestId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM reservation
                WHERE room_id = :roomId
                  AND occupied_period && tstzrange(:startsAt, :occupiedUntil, '[)')
            )
            """, nativeQuery = true)
    boolean existsRoomOccupancyConflict(@Param("roomId") UUID roomId,
                                        @Param("startsAt") Instant startsAt,
                                        @Param("occupiedUntil") Instant occupiedUntil);
}
