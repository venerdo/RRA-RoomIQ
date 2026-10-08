package rw.rra.roomiq.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.identity.domain.entity.IdentityAuditOutboxEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IdentityAuditOutboxRepository extends JpaRepository<IdentityAuditOutboxEntry, UUID> {
    java.util.Optional<IdentityAuditOutboxEntry> findTopByEventTypeOrderByCreatedAtDesc(String eventType);

    List<IdentityAuditOutboxEntry> findTop50ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Instant now);
}