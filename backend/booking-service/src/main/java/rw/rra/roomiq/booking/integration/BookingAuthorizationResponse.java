package rw.rra.roomiq.booking.integration;

import java.util.UUID;

public record BookingAuthorizationResponse(
        UUID actorUserId,
        UUID resourceOwnerUserId,
        String resourceOwnerDisplayName,
        DirectBookingAuthority directBookingAuthority,
        CancellationAuthority cancellationAuthority) {
    public BookingAuthorizationResponse(UUID actorUserId, UUID resourceOwnerUserId,
                                       String resourceOwnerDisplayName) {
        this(actorUserId, resourceOwnerUserId, resourceOwnerDisplayName, null, null);
    }

    public BookingAuthorizationResponse(UUID actorUserId, UUID resourceOwnerUserId,
                                        String resourceOwnerDisplayName,
                                        DirectBookingAuthority directBookingAuthority) {
        this(actorUserId, resourceOwnerUserId, resourceOwnerDisplayName, directBookingAuthority, null);
    }

    public enum DirectBookingAuthority {
        ADMIN,
        SECRETARY
    }

    public enum CancellationAuthority {
        OWNER,
        ADMIN,
        SYSTEM_ADMIN
    }
}
