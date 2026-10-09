package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Type;
import org.postgresql.util.PGobject;
import rw.rra.roomiq.booking.domain.type.PostgreSqlTstzRangeType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_occurrence",
        uniqueConstraints = @UniqueConstraint(name = "uq_reservation_occurrence_series_start",
                columnNames = {"reservation_id", "start_at"}))
public class ReservationOccurrence extends BookingEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Type(PostgreSqlTstzRangeType.class)
    @Column(name = "occupied_period", nullable = false, columnDefinition = "tstzrange")
    private PGobject occupiedPeriod;

    protected ReservationOccurrence() {
    }

    public ReservationOccurrence(Reservation reservation, UUID roomId, Instant startAt, Instant endAt,
                                 PGobject occupiedPeriod) {
        this.reservation = reservation;
        this.roomId = roomId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.occupiedPeriod = occupiedPeriod;
    }

    public Reservation getReservation() { return reservation; }
    public UUID getRoomId() { return roomId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public PGobject getOccupiedPeriod() { return occupiedPeriod; }
}
