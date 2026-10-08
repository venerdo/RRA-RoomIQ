package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SetWorkingCalendarRequest(
        @NotBlank @Size(max = 150) String name,
        UUID officeBuildingId,
        @NotBlank @Size(max = 64) String timezone,
        boolean defaultCalendar,
        boolean active) {
}