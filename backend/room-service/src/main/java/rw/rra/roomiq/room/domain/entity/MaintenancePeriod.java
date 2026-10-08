package rw.rra.roomiq.room.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.postgresql.util.PGobject;
import rw.rra.roomiq.room.domain.type.PostgreSqlTstzRangeType;

import java.util.UUID;

@Entity
@Table(name = "maintenance_period")
public class MaintenancePeriod extends RoomEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Type(PostgreSqlTstzRangeType.class)
    @Column(nullable = false, columnDefinition = "tstzrange")
    private PGobject period;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    protected MaintenancePeriod() {
    }

    public MaintenancePeriod(Room room, PGobject period, String reason, UUID createdByUserId) {
        this.room = room;
        this.period = period;
        this.reason = reason;
        this.createdByUserId = createdByUserId;
    }

    public Room getRoom() { return room; }
    public PGobject getPeriod() { return period; }
    public String getReason() { return reason; }
    public UUID getCreatedByUserId() { return createdByUserId; }
}