package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.postgresql.util.PGobject;
import rw.rra.roomiq.booking.domain.type.PostgreSqlJsonbType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_event_outbox")
public class BookingEventOutboxEntry extends BookingEntity {
    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

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

    @Type(PostgreSqlJsonbType.class)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
    private PGobject payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected BookingEventOutboxEntry() {
    }

    public BookingEventOutboxEntry(UUID eventId, String eventType, int eventVersion, String aggregateType,
                                   UUID aggregateId, long aggregateVersion, PGobject payload, Instant occurredAt,
                                   Instant createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public PGobject getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void deferUntil(Instant retryAt, String failure) {
        attemptCount++;
        nextAttemptAt = retryAt;
        lastError = failure;
    }
}
