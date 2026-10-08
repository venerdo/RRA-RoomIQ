package rw.rra.roomiq.scheduling.domain.dto;

import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;

import java.util.UUID;

public record WorkingCalendarResponse(
        UUID id,
        String name,
        UUID officeBuildingId,
        String timezone,
        boolean defaultCalendar,
        boolean active) {
    public static WorkingCalendarResponse from(WorkingCalendar calendar) {
        return new WorkingCalendarResponse(calendar.getId(), calendar.getName(), calendar.getOfficeBuildingId(),
                calendar.getTimezone(), calendar.isDefaultCalendar(), calendar.isActive());
    }
}