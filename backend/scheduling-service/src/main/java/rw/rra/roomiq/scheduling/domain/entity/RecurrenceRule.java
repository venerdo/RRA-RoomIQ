package rw.rra.roomiq.scheduling.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurrence_rule")
public class RecurrenceRule extends SchedulingEntity {
    @Column(nullable = false, length = 1000)
    private String rrule;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecurrenceRule() {
    }

    public RecurrenceRule(String rrule, LocalDate startsOn, LocalDate endsOn, Integer occurrenceCount,
                          String timezone, UUID createdByUserId) {
        this.rrule = rrule;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.occurrenceCount = occurrenceCount;
        this.timezone = timezone;
        this.createdByUserId = createdByUserId;
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getRrule() { return rrule; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public Integer getOccurrenceCount() { return occurrenceCount; }
    public String getTimezone() { return timezone; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}