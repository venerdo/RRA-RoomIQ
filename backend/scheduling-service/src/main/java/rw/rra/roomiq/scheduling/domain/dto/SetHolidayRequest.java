package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record SetHolidayRequest(
        UUID workingCalendarId,
        UUID officeBuildingId,
        @NotNull LocalDate holidayDate,
        @NotBlank @Size(max = 200) String name,
        @NotNull Boolean blocksBooking,
        @NotNull Boolean active) {
}