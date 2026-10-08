package rw.rra.roomiq.room.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "facility_type")
public class FacilityType extends RoomEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String category;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected FacilityType() {
    }

    public FacilityType(String code, String name, String category, boolean active) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public boolean isActive() { return active; }

    public void update(String code, String name, String category, boolean active) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.active = active;
    }
}