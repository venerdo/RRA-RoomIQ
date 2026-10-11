package rw.rra.roomiq.booking.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record MeetingShareLinkResponse(UUID shareLinkId, Instant expiresAt, Instant revokedAt,
                                       int viewCount, String timezone) {
}
