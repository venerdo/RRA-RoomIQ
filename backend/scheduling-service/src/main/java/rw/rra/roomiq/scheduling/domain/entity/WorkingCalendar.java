package rw.rra.roomiq.scheduling.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "working_calendar")
public class WorkingCalendar extends SchedulingEntity {
    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "office_building_id")
    private UUID officeBuildingId;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "is_default", nullable = false)
    private boolean defaultCalendar;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected WorkingCalendar() {
    }

    public WorkingCalendar(String name, UUID officeBuildingId, String timezone,
                           boolean defaultCalendar, boolean active) {
        this.name = name;
        this.officeBuildingId = officeBuildingId;
        this.timezone = timezone;
        this.defaultCalendar = defaultCalendar;
        this.active = active;
    }

    public String getName() { return name; }
    public UUID getOfficeBuildingId() { return officeBuildingId; }
    public String getTimezone() { return timezone; }
    public boolean isDefaultCalendar() { return defaultCalendar; }
    public boolean isActive() { return active; }

    public void update(String name, UUID officeBuildingId, String timezone,
                       boolean defaultCalendar, boolean active) {
        this.name = name;
        this.officeBuildingId = officeBuildingId;
        this.timezone = timezone;
        this.defaultCalendar = defaultCalendar;
        this.active = active;
    }
}