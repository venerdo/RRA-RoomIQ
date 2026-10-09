package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.booking.domain.entity.BookingExtension;

import java.util.UUID;

public interface BookingExtensionRepository extends JpaRepository<BookingExtension, UUID> {
}
