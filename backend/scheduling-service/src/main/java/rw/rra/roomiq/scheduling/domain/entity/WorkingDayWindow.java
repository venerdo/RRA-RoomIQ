package rw.rra.roomiq.scheduling.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "working_day_window")
public class WorkingDayWindow extends SchedulingEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "working_calendar_id", nullable = false)
    private WorkingCalendar workingCalendar;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "is_working_day", nullable = false)
    private boolean workingDay;

    protected WorkingDayWindow() {
    }

    public WorkingDayWindow(WorkingCalendar workingCalendar, short dayOfWeek,
                            LocalTime openTime, LocalTime closeTime, boolean workingDay) {
        this.workingCalendar = workingCalendar;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.workingDay = workingDay;
    }

    public WorkingCalendar getWorkingCalendar() { return workingCalendar; }
    public short getDayOfWeek() { return dayOfWeek; }
    public LocalTime getOpenTime() { return openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public boolean isWorkingDay() { return workingDay; }

    public void update(short dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean workingDay) {
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.workingDay = workingDay;
    }
}