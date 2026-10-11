package rw.rra.roomiq.booking.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.booking.domain.entity.Meeting;

import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
    Optional<Meeting> findByReservation_Id(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from Meeting meeting where meeting.id = :meetingId")
    Optional<Meeting> findByIdForUpdate(@Param("meetingId") UUID meetingId);
}
