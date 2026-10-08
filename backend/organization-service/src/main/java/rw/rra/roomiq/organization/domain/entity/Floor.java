package rw.rra.roomiq.organization.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "floor", uniqueConstraints = @UniqueConstraint(
        name = "uq_floor_building_name", columnNames = {"office_building_id", "name"}))
public class Floor extends OrganizationEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "office_building_id", nullable = false)
    private OfficeBuilding officeBuilding;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private short level;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Floor() {
    }

    public Floor(OfficeBuilding officeBuilding, String name, short level, boolean active) {
        this.officeBuilding = officeBuilding;
        this.name = name;
        this.level = level;
        this.active = active;
    }

    public void updateProfile(OfficeBuilding officeBuilding, String name, short level) {
        this.officeBuilding = officeBuilding;
        this.name = name;
        this.level = level;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OfficeBuilding getOfficeBuilding() {
        return officeBuilding;
    }

    public String getName() {
        return name;
    }

    public short getLevel() {
        return level;
    }

    public boolean isActive() {
        return active;
    }
}