package rw.rra.roomiq.scheduling.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import rw.rra.roomiq.scheduling.domain.entity.ClosurePeriod;

import java.util.UUID;

public interface ClosurePeriodRepository extends JpaRepository<ClosurePeriod, UUID>,
	JpaSpecificationExecutor<ClosurePeriod> {
}