package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SetWorkingDayWindowRequest(
        @Min(1) @Max(7) short dayOfWeek,
        @NotNull LocalTime openTime,
        @NotNull LocalTime closeTime,
        boolean workingDay) {
}