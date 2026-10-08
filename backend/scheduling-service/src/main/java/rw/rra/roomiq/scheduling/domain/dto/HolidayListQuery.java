package rw.rra.roomiq.scheduling.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record HolidayListQuery(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        UUID workingCalendarId,
        UUID officeBuildingId,
        Boolean nationwide,
        Boolean active,
        Boolean blocksBooking,
        LocalDate fromDate,
        LocalDate toDate,
        @Pattern(regexp = "(?i)holidayDate|name") String sortBy,
        @Pattern(regexp = "(?i)ASC|DESC") String sortDirection) {
    public int pageNumber() { return page == null ? 0 : page; }
    public int pageSize() { return size == null ? 20 : size; }
}