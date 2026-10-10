package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Type;
import org.postgresql.util.PGobject;
import rw.rra.roomiq.booking.domain.enums.ReservationStatus;
import rw.rra.roomiq.booking.domain.type.PostgreSqlTstzRangeType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation")
public class Reservation extends BookingEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "booking_request_id", nullable = false, unique = true)
    private BookingRequest bookingRequest;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "organizer_user_id", nullable = false)
    private UUID organizerUserId;

    @Column(name = "recurrence_rule_id")
    private UUID recurrenceRuleId;

    @Type(PostgreSqlTstzRangeType.class)
    @Column(name = "occupied_period", nullable = false, columnDefinition = "tstzrange")
    private PGobject occupiedPeriod;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "release_buffer_minutes", nullable = false)
    private int releaseBufferMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private ReservationStatus status;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reservation() {
    }

    public Reservation(BookingRequest bookingRequest, UUID roomId, UUID organizerUserId, UUID recurrenceRuleId,
                       PGobject occupiedPeriod, Instant startAt, Instant endAt, int releaseBufferMinutes,
                       ReservationStatus status, Instant createdAt) {
        this.bookingRequest = bookingRequest;
        this.roomId = roomId;
        this.organizerUserId = organizerUserId;
        this.recurrenceRuleId = recurrenceRuleId;
        this.occupiedPeriod = occupiedPeriod;
        this.startAt = startAt;
        this.endAt = endAt;
        this.releaseBufferMinutes = releaseBufferMinutes;
        this.status = status;
        this.createdAt = createdAt;
    }

    public BookingRequest getBookingRequest() { return bookingRequest; }
    public UUID getRoomId() { return roomId; }
    public UUID getOrganizerUserId() { return organizerUserId; }
    public UUID getRecurrenceRuleId() { return recurrenceRuleId; }
    public PGobject getOccupiedPeriod() { return occupiedPeriod; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public int getReleaseBufferMinutes() { return releaseBufferMinutes; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean checkIn(Instant at) {
        if (at == null || status != ReservationStatus.CONFIRMED || checkedInAt != null || completedAt != null) {
            return false;
        }
        checkedInAt = at;
        status = ReservationStatus.IN_PROGRESS;
        return true;
    }

    public boolean complete(Instant at) {
        if (at == null || status != ReservationStatus.IN_PROGRESS || checkedInAt == null
                || completedAt != null || at.isBefore(checkedInAt)) {
            return false;
        }
        completedAt = at;
        status = ReservationStatus.COMPLETED;
        return true;
    }
}
