package rw.rra.roomiq.booking.domain.dto;

import java.util.List;

public record BookingRequestPageResponse(
        List<BookingRequestResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
