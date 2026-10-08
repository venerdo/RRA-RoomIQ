package rw.rra.roomiq.organization.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "district", uniqueConstraints = @UniqueConstraint(
        name = "uq_district_province_name", columnNames = {"province_id", "name"}))
public class District extends OrganizationEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_id", nullable = false)
    private Province province;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected District() {
    }

    public District(Province province, String name, boolean active) {
        this.province = province;
        this.name = name;
        this.active = active;
    }

    public void updateProfile(Province province, String name) {
        this.province = province;
        this.name = name;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Province getProvince() {
        return province;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}