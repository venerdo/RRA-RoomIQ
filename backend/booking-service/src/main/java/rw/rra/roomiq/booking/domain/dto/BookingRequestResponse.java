package rw.rra.roomiq.booking.domain.dto;

import rw.rra.roomiq.booking.domain.entity.BookingRequest;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.BookingRequestType;

import java.time.Instant;
import java.util.UUID;

public record BookingRequestResponse(
        UUID id,
        String requestReference,
        BookingRequestType requestType,
        UUID requestedByUserId,
        UUID departmentId,
        UUID roomId,
        UUID officeBuildingId,
        UUID recurrenceRuleId,
        String title,
        String purpose,
        Instant requestedStart,
        Instant requestedEnd,
        int attendeeCount,
        Boolean externalGuests,
        BookingRequestStatus status,
        int version,
        Instant createdAt) {
    public static BookingRequestResponse from(BookingRequest request) {
        return new BookingRequestResponse(request.getId(), request.getRequestReference(), request.getRequestType(),
                request.getRequestedByUserId(), request.getDepartmentId(), request.getRoomId(),
                request.getOfficeBuildingId(), request.getRecurrenceRuleId(), request.getTitle(),
                request.getPurpose(), request.getRequestedStart(), request.getRequestedEnd(),
                request.getAttendeeCount(), request.getExternalGuests(), request.getStatus(),
                request.getVersion(), request.getCreatedAt());
    }
}
