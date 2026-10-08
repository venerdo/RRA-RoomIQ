package rw.rra.roomiq.room.domain.dto;

import java.util.List;

public record CatalogPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sortBy,
        String sortDirection
) {
}
