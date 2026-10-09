package rw.rra.roomiq.identity.domain.dto;

import java.util.UUID;

public record BookingAuthorizationResponse(
        UUID actorUserId,
        UUID resourceOwnerUserId,
        String resourceOwnerDisplayName) {
}
