package rw.rra.roomiq.organization.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "office_building", uniqueConstraints = @UniqueConstraint(
        name = "uq_office_building_code", columnNames = "code"))
public class OfficeBuilding extends OrganizationEntity {
    public static final String DEFAULT_TIMEZONE = "Africa/Kigali";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id", nullable = false)
    private District district;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false, length = 64)
    private String timezone = DEFAULT_TIMEZONE;

    @Column(name = "working_calendar_id")
    private UUID workingCalendarId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected OfficeBuilding() {
    }

    public OfficeBuilding(District district, String name, String code, String address,
                          String timezone, UUID workingCalendarId, boolean active) {
        this.district = district;
        this.name = name;
        this.code = code;
        this.address = address;
        this.timezone = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
        this.workingCalendarId = workingCalendarId;
        this.active = active;
    }

    public void updateProfile(District district, String name, String code, String address,
                              String timezone, UUID workingCalendarId) {
        this.district = district;
        this.name = name;
        this.code = code;
        this.address = address;
        this.timezone = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
        this.workingCalendarId = workingCalendarId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public District getDistrict() {
        return district;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getAddress() {
        return address;
    }

    public String getTimezone() {
        return timezone;
    }

    public UUID getWorkingCalendarId() {
        return workingCalendarId;
    }

    public boolean isActive() {
        return active;
    }
}