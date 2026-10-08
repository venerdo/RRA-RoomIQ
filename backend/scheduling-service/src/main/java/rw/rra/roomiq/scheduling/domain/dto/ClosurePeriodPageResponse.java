package rw.rra.roomiq.scheduling.domain.dto;

import java.util.List;

public record ClosurePeriodPageResponse(
        List<ClosurePeriodResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sortBy,
        String sortDirection) {
}