package rw.rra.roomiq.room.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record FacilityTypePageQuery(
        @Size(max = 100) String search,
        Boolean active,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9]*") String sortBy,
        @Pattern(regexp = "(?i)ASC|DESC") String sortDirection
) {
    public int pageIndex() {
        return page == null ? 0 : page;
    }

    public int pageSize() {
        return size == null ? 20 : size;
    }

    public String searchTerm() {
        return search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
    }
}
