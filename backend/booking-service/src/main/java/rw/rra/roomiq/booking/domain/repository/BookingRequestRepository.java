package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.booking.domain.entity.BookingRequest;

import java.util.UUID;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, UUID> {
}
