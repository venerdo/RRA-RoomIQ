package rw.rra.roomiq.scheduling.domain.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecurrenceOccurrencesResponse(
        UUID recurrenceRuleId,
        String timezone,
        List<LocalDate> occurrences) {
}
