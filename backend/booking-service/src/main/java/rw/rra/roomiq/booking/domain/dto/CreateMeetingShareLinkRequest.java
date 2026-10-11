package rw.rra.roomiq.booking.domain.dto;

import java.time.Instant;

public record CreateMeetingShareLinkRequest(Instant expiresAt) {
}
