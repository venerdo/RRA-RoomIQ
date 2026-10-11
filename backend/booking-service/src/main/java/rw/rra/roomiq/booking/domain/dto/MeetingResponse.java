package rw.rra.roomiq.booking.domain.dto;

import rw.rra.roomiq.booking.domain.enums.MeetingVisibility;
import rw.rra.roomiq.booking.domain.enums.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record MeetingResponse(
        UUID meetingId,
        UUID reservationId,
        UUID organizerUserId,
        UUID roomId,
        UUID buildingId,
        UUID departmentId,
        Instant startAt,
        Instant endAt,
        ReservationStatus reservationStatus,
        String title,
        String agenda,
        String organizerDisplayName,
        String instructions,
        String contactInfo,
        MeetingVisibility visibility,
        Instant createdAt) {
}
