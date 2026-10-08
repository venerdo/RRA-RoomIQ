package rw.rra.roomiq.organization.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "province", uniqueConstraints = @UniqueConstraint(
        name = "uq_province_country_name", columnNames = {"country_id", "name"}))
public class Province extends OrganizationEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Province() {
    }

    public Province(Country country, String name, boolean active) {
        this.country = country;
        this.name = name;
        this.active = active;
    }

    public void updateProfile(Country country, String name) {
        this.country = country;
        this.name = name;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Country getCountry() {
        return country;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}