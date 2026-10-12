package rw.rra.roomiq.booking.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record MeetingShareLinkCreatedResponse(UUID shareLinkId, String token, Instant expiresAt, boolean replayed) {
}
