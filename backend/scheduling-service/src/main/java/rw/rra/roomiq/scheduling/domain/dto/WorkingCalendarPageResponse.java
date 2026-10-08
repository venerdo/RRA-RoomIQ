package rw.rra.roomiq.scheduling.domain.dto;

import java.util.List;

public record WorkingCalendarPageResponse(
        List<WorkingCalendarResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sortBy,
        String sortDirection) {
}