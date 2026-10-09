package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.booking.domain.entity.Cancellation;

import java.util.UUID;

public interface CancellationRepository extends JpaRepository<Cancellation, UUID> {
}
