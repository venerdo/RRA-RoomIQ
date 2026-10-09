package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.booking.domain.entity.Meeting;

import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {
}
