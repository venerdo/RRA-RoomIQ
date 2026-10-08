package rw.rra.roomiq.room.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "room_facility", uniqueConstraints = @UniqueConstraint(
        name = "uq_room_facility_type", columnNames = {"room_id", "facility_type_id"}))
public class RoomFacility extends RoomEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_type_id", nullable = false)
    private FacilityType facilityType;

    @Column(nullable = false)
    private short quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FacilityState state;

    @Column(name = "last_serviced_at")
    private Instant lastServicedAt;

    protected RoomFacility() {
    }

    public RoomFacility(Room room, FacilityType facilityType, short quantity,
                        FacilityState state, Instant lastServicedAt) {
        this.room = room;
        this.facilityType = facilityType;
        this.quantity = quantity;
        this.state = state;
        this.lastServicedAt = lastServicedAt;
    }

    public Room getRoom() { return room; }
    public FacilityType getFacilityType() { return facilityType; }
    public short getQuantity() { return quantity; }
    public FacilityState getState() { return state; }
    public Instant getLastServicedAt() { return lastServicedAt; }

    public void updateDetails(FacilityType facilityType, short quantity, FacilityState state, Instant lastServicedAt) {
        this.facilityType = facilityType;
        this.quantity = quantity;
        this.state = state;
        this.lastServicedAt = lastServicedAt;
    }
}