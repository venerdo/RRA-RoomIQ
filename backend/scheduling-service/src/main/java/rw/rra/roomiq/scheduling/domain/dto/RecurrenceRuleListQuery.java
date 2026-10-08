package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RecurrenceRuleListQuery(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {
    public int pageNumber() {
        return page == null ? 0 : page;
    }

    public int pageSize() {
        return size == null ? 20 : size;
    }
}
