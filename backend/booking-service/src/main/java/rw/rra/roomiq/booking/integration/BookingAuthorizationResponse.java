package rw.rra.roomiq.booking.integration;

import java.util.UUID;

public record BookingAuthorizationResponse(
        UUID actorUserId,
        UUID resourceOwnerUserId,
        String resourceOwnerDisplayName,
        DirectBookingAuthority directBookingAuthority) {
    public BookingAuthorizationResponse(UUID actorUserId, UUID resourceOwnerUserId,
                                       String resourceOwnerDisplayName) {
        this(actorUserId, resourceOwnerUserId, resourceOwnerDisplayName, null);
    }

    public enum DirectBookingAuthority {
        ADMIN,
        SECRETARY
    }
}
