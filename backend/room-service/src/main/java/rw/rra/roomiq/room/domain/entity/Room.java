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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room")
public class Room extends RoomEntity {
    @Column(name = "floor_id", nullable = false)
    private UUID floorId;

    @Column(name = "office_building_id", nullable = false)
    private UUID officeBuildingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "class", nullable = false, length = 16)
    private RoomClass roomClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoomStatus status;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Room() {
    }

    public Room(RoomType roomType, UUID floorId, UUID officeBuildingId, String name, String code,
                String description, int capacity, RoomClass roomClass, RoomStatus status) {
        this.roomType = roomType;
        this.floorId = floorId;
        this.officeBuildingId = officeBuildingId;
        this.name = name;
        this.code = code;
        this.description = description;
        this.capacity = capacity;
        this.roomClass = roomClass;
        this.status = status;
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getFloorId() { return floorId; }
    public UUID getOfficeBuildingId() { return officeBuildingId; }
    public RoomType getRoomType() { return roomType; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public int getCapacity() { return capacity; }
    public RoomClass getRoomClass() { return roomClass; }
    public RoomStatus getStatus() { return status; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void changeStatus(RoomStatus status) {
        if (this.status == RoomStatus.DECOMMISSIONED || this.status == status) {
            throw new IllegalArgumentException("Room status transition is not allowed");
        }
        this.status = status;
    }

    public void softDelete(Instant deletedAt) {
        if (this.deletedAt != null) {
            throw new IllegalStateException("Room is already deleted");
        }
        this.deletedAt = deletedAt;
    }

    public void updateDetails(RoomType roomType, UUID floorId, UUID officeBuildingId, String name, String code,
                             String description, int capacity, RoomClass roomClass, RoomStatus status) {
        this.roomType = roomType;
        this.floorId = floorId;
        this.officeBuildingId = officeBuildingId;
        this.name = name;
        this.code = code;
        this.description = description;
        this.capacity = capacity;
        this.roomClass = roomClass;
        this.status = status;
    }
}