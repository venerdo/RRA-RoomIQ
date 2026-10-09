package rw.rra.roomiq.scheduling.domain.dto;

import java.time.Instant;
import java.time.LocalDate;

public record SchedulingConstraintOccurrence(
        LocalDate occurrenceDate,
        Instant startsAt,
        Instant endsAt) {
}
