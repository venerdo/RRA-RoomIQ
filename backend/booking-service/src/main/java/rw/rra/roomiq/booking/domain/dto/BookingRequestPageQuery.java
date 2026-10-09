package rw.rra.roomiq.booking.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;

import java.util.UUID;

public record BookingRequestPageQuery(
        UUID officeBuildingId,
        BookingRequestStatus status,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {
    public int pageNumber() {
        return page == null ? 0 : page;
    }

    public int pageSize() {
        return size == null ? 20 : size;
    }
}
