package rw.rra.roomiq.scheduling.domain.dto;

import rw.rra.roomiq.scheduling.domain.entity.RecurrenceRule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RecurrenceRuleResponse(
        UUID id,
        String rrule,
        LocalDate startsOn,
        LocalDate endsOn,
        Integer occurrenceCount,
        String timezone,
        UUID createdByUserId,
        Instant createdAt) {
    public static RecurrenceRuleResponse from(RecurrenceRule rule) {
        return new RecurrenceRuleResponse(rule.getId(), rule.getRrule(), rule.getStartsOn(),
                rule.getEndsOn(), rule.getOccurrenceCount(), rule.getTimezone(),
                rule.getCreatedByUserId(), rule.getCreatedAt());
    }
}
