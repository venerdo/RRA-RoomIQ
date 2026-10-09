package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_decision")
public class ApprovalDecision extends BookingEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "booking_request_id", nullable = false, unique = true)
    private BookingRequest bookingRequest;

    @Column(name = "decided_by_user_id", nullable = false)
    private UUID decidedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private ApprovalDecisionType decision;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ApprovalDecision() {
    }

    public ApprovalDecision(BookingRequest bookingRequest, UUID decidedByUserId,
                            ApprovalDecisionType decision, String comment, Instant decidedAt) {
        this.bookingRequest = bookingRequest;
        this.decidedByUserId = decidedByUserId;
        this.decision = decision;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    public BookingRequest getBookingRequest() { return bookingRequest; }
    public UUID getDecidedByUserId() { return decidedByUserId; }
    public ApprovalDecisionType getDecision() { return decision; }
    public String getComment() { return comment; }
    public Instant getDecidedAt() { return decidedAt; }
}
