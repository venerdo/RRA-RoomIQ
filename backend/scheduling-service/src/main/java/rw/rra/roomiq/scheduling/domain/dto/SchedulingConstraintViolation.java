package rw.rra.roomiq.scheduling.domain.dto;

import java.time.LocalDate;

public record SchedulingConstraintViolation(LocalDate occurrenceDate, String code, String message) {
}
