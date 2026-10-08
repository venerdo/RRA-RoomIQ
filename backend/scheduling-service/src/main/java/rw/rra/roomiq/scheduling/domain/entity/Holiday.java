package rw.rra.roomiq.scheduling.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "holiday")
public class Holiday extends SchedulingEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "working_calendar_id")
    private WorkingCalendar workingCalendar;

    @Column(name = "office_building_id")
    private UUID officeBuildingId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "blocks_booking", nullable = false)
    private boolean blocksBooking;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    protected Holiday() {
    }

    public Holiday(WorkingCalendar workingCalendar, UUID officeBuildingId, LocalDate holidayDate,
                   String name, boolean blocksBooking, boolean active, UUID createdByUserId) {
        this.workingCalendar = workingCalendar;
        this.officeBuildingId = officeBuildingId;
        this.holidayDate = holidayDate;
        this.name = name;
        this.blocksBooking = blocksBooking;
        this.active = active;
        this.createdByUserId = createdByUserId;
    }

    public WorkingCalendar getWorkingCalendar() { return workingCalendar; }
    public UUID getOfficeBuildingId() { return officeBuildingId; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getName() { return name; }
    public boolean isBlocksBooking() { return blocksBooking; }
    public boolean isActive() { return active; }
    public UUID getCreatedByUserId() { return createdByUserId; }

    public void update(WorkingCalendar workingCalendar, UUID officeBuildingId, LocalDate holidayDate,
                       String name, boolean blocksBooking, boolean active) {
        this.workingCalendar = workingCalendar;
        this.officeBuildingId = officeBuildingId;
        this.holidayDate = holidayDate;
        this.name = name;
        this.blocksBooking = blocksBooking;
        this.active = active;
    }
}