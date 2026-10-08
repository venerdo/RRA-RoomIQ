package rw.rra.roomiq.room.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_status_history")
public class RoomStatusHistory extends RoomEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 32)
    private RoomStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private RoomStatus newStatus;

    @Column(name = "changed_by_user_id", nullable = false)
    private UUID changedByUserId;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected RoomStatusHistory() {
    }

    public RoomStatusHistory(Room room, RoomStatus oldStatus, RoomStatus newStatus,
                             UUID changedByUserId, String reason) {
        this.room = room;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedByUserId = changedByUserId;
        this.reason = reason;
    }

    @PrePersist
    void initializeChangedAt() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public Room getRoom() { return room; }
    public RoomStatus getOldStatus() { return oldStatus; }
    public RoomStatus getNewStatus() { return newStatus; }
    public UUID getChangedByUserId() { return changedByUserId; }
    public String getReason() { return reason; }
    public Instant getChangedAt() { return changedAt; }
}