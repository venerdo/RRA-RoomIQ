package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.booking.domain.entity.MeetingShareLink;

import java.util.UUID;

public interface MeetingShareLinkRepository extends JpaRepository<MeetingShareLink, UUID> {
}
