package rw.rra.roomiq.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_event_receipt")
public class NotificationEventReceipt {
    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "source_service", nullable = false, length = 64, updatable = false)
    private String sourceService;

    @Column(name = "event_type", nullable = false, length = 128, updatable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @Column(name = "aggregate_type", nullable = false, length = 64, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private long aggregateVersion;

    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;

    @Column(name = "causation_id", length = 128, updatable = false)
    private String causationId;

    @Column(name = "resource_type", length = 64, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected NotificationEventReceipt() {
    }

    public NotificationEventReceipt(UUID id, UUID eventId, String sourceService, String eventType,
                                    int eventVersion, String aggregateType, UUID aggregateId,
                                    long aggregateVersion, String correlationId, String causationId,
                                    String resourceType, UUID resourceId, Instant processedAt) {
        this.id = id;
        this.eventId = eventId;
        this.sourceService = sourceService;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.processedAt = processedAt;
    }

    public UUID getEventId() { return eventId; }
    public String getSourceService() { return sourceService; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }

    public boolean matches(NotificationEventReceipt other) {
        return eventId.equals(other.eventId)
                && sourceService.equals(other.sourceService)
                && eventType.equals(other.eventType)
                && eventVersion == other.eventVersion
                && aggregateType.equals(other.aggregateType)
                && aggregateId.equals(other.aggregateId)
                && aggregateVersion == other.aggregateVersion
                && java.util.Objects.equals(correlationId, other.correlationId)
                && java.util.Objects.equals(causationId, other.causationId)
                && java.util.Objects.equals(resourceType, other.resourceType)
                && java.util.Objects.equals(resourceId, other.resourceId);
    }
}
