package rw.rra.roomiq.scheduling.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.scheduling.domain.entity.RecurrenceRule;

import java.util.UUID;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {
}