package rw.rra.roomiq.scheduling.domain.dto;

import java.util.List;

public record RecurrenceRulePageResponse(
        List<RecurrenceRuleResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
