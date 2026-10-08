package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SetRecurrenceRuleRequest(
        @NotBlank @Size(max = 1000) String rrule,
        @NotNull LocalDate startsOn,
        @NotBlank @Size(max = 64) String timezone) {
}
