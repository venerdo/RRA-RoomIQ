package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import rw.rra.roomiq.booking.domain.enums.BookingExtensionStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_extension")
public class BookingExtension extends BookingEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "previous_end_at", nullable = false)
    private Instant previousEndAt;

    @Column(name = "requested_end_at", nullable = false)
    private Instant requestedEndAt;

    @Column(name = "approved_end_at")
    private Instant approvedEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private BookingExtensionStatus status;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;

    @Column(name = "idempotency_key", unique = true, columnDefinition = "varchar")
    private String idempotencyKey;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected BookingExtension() {
    }

    public BookingExtension(Reservation reservation, UUID requestedByUserId, Instant previousEndAt,
                            Instant requestedEndAt, Instant approvedEndAt, BookingExtensionStatus status,
                            UUID decidedByUserId, String decisionComment, String idempotencyKey,
                            Instant requestedAt, Instant decidedAt) {
        this.reservation = reservation;
        this.requestedByUserId = requestedByUserId;
        this.previousEndAt = previousEndAt;
        this.requestedEndAt = requestedEndAt;
        this.approvedEndAt = approvedEndAt;
        this.status = status;
        this.decidedByUserId = decidedByUserId;
        this.decisionComment = decisionComment;
        this.idempotencyKey = idempotencyKey;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
    }

    public Reservation getReservation() { return reservation; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public Instant getPreviousEndAt() { return previousEndAt; }
    public Instant getRequestedEndAt() { return requestedEndAt; }
    public Instant getApprovedEndAt() { return approvedEndAt; }
    public BookingExtensionStatus getStatus() { return status; }
    public UUID getDecidedByUserId() { return decidedByUserId; }
    public String getDecisionComment() { return decisionComment; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
