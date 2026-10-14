package rw.rra.roomiq.notification.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.notification.domain.entity.NotificationEventReceipt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationEventReceiptRepository extends JpaRepository<NotificationEventReceipt, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO notification_event_receipt
                (id, event_id, source_service, event_type, event_version, aggregate_type,
                 aggregate_id, aggregate_version, correlation_id, causation_id, resource_type, resource_id, processed_at)
            VALUES
                (:id, :eventId, :sourceService, :eventType, :eventVersion, :aggregateType,
                 :aggregateId, :aggregateVersion, :correlationId, :causationId, :resourceType, :resourceId, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("eventId") UUID eventId,
                       @Param("sourceService") String sourceService,
                       @Param("eventType") String eventType,
                       @Param("eventVersion") int eventVersion,
                       @Param("aggregateType") String aggregateType,
                       @Param("aggregateId") UUID aggregateId,
                       @Param("aggregateVersion") long aggregateVersion,
                       @Param("correlationId") String correlationId,
                       @Param("causationId") String causationId,
                       @Param("resourceType") String resourceType,
                       @Param("resourceId") UUID resourceId,
                       @Param("processedAt") Instant processedAt);

    Optional<NotificationEventReceipt> findByEventId(UUID eventId);

    boolean existsByResourceTypeAndResourceIdAndEventTypeIn(
            String resourceType, UUID resourceId, java.util.Collection<String> eventTypes);
}
