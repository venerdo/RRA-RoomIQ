package rw.rra.roomiq.scheduling.domain.dto;

import rw.rra.roomiq.scheduling.domain.entity.Holiday;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayResponse(
        UUID id,
        UUID workingCalendarId,
        UUID officeBuildingId,
        LocalDate holidayDate,
        String name,
        boolean blocksBooking,
        boolean active,
        UUID createdByUserId) {
    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(holiday.getId(),
                holiday.getWorkingCalendar() == null ? null : holiday.getWorkingCalendar().getId(),
                holiday.getOfficeBuildingId(), holiday.getHolidayDate(), holiday.getName(),
                holiday.isBlocksBooking(), holiday.isActive(), holiday.getCreatedByUserId());
    }
}