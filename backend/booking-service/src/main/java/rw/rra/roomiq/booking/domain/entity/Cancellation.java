package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import rw.rra.roomiq.booking.domain.enums.CancellationReason;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cancellation")
public class Cancellation extends BookingEntity {
    @ManyToOne
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne
    @JoinColumn(name = "booking_request_id")
    private BookingRequest bookingRequest;

    @Column(name = "cancelled_by_user_id", nullable = false)
    private UUID cancelledByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, columnDefinition = "varchar")
    private CancellationReason reasonCode;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "cancellation_deadline_at")
    private Instant cancellationDeadlineAt;

    @Column(name = "override_applied", nullable = false)
    private boolean overrideApplied;

    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;

    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;

    protected Cancellation() {
    }

    public Cancellation(Reservation reservation, BookingRequest bookingRequest, UUID cancelledByUserId,
                        CancellationReason reasonCode, String reason, Instant cancellationDeadlineAt,
                        boolean overrideApplied, String overrideReason, Instant cancelledAt) {
        this.reservation = reservation;
        this.bookingRequest = bookingRequest;
        this.cancelledByUserId = cancelledByUserId;
        this.reasonCode = reasonCode;
        this.reason = reason;
        this.cancellationDeadlineAt = cancellationDeadlineAt;
        this.overrideApplied = overrideApplied;
        this.overrideReason = overrideReason;
        this.cancelledAt = cancelledAt;
    }

    public Reservation getReservation() { return reservation; }
    public BookingRequest getBookingRequest() { return bookingRequest; }
    public UUID getCancelledByUserId() { return cancelledByUserId; }
    public CancellationReason getReasonCode() { return reasonCode; }
    public String getReason() { return reason; }
    public Instant getCancellationDeadlineAt() { return cancellationDeadlineAt; }
    public boolean isOverrideApplied() { return overrideApplied; }
    public String getOverrideReason() { return overrideReason; }
    public Instant getCancelledAt() { return cancelledAt; }
}
