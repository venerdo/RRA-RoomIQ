package rw.rra.roomiq.booking.domain.dto;

import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.BookingRequestType;

import java.util.UUID;

public record DirectBookingResponse(
        UUID bookingRequestId,
        String requestReference,
        BookingRequestType requestType,
        BookingRequestStatus status,
        UUID reservationId,
        UUID meetingId,
        int occurrenceCount) {
}
