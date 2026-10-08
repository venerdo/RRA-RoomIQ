package rw.rra.roomiq.scheduling.domain.dto;

import rw.rra.roomiq.scheduling.domain.entity.WorkingDayWindow;

import java.time.LocalTime;
import java.util.UUID;

public record WorkingDayWindowResponse(
        UUID id,
        UUID workingCalendarId,
        short dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime,
        boolean workingDay) {
    public static WorkingDayWindowResponse from(WorkingDayWindow window) {
        return new WorkingDayWindowResponse(window.getId(), window.getWorkingCalendar().getId(),
                window.getDayOfWeek(), window.getOpenTime(), window.getCloseTime(), window.isWorkingDay());
    }
}