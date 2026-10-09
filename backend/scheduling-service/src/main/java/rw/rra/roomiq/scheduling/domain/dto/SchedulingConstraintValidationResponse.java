package rw.rra.roomiq.scheduling.domain.dto;

import java.util.List;
import java.util.UUID;

public record SchedulingConstraintValidationResponse(
        UUID workingCalendarId,
        UUID recurrenceRuleId,
        String timezone,
        boolean valid,
        int occurrencesEvaluated,
        List<SchedulingConstraintViolation> violations) {
}
