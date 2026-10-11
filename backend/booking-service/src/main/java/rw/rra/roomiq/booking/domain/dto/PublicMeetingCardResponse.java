package rw.rra.roomiq.booking.domain.dto;

import java.time.OffsetDateTime;

public record PublicMeetingCardResponse(String title, OffsetDateTime startAt, OffsetDateTime endAt,
                                       String timezone, String organizerDisplayName) {
}
