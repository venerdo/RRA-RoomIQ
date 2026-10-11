package rw.rra.roomiq.identity.domain.dto;

import rw.rra.roomiq.identity.domain.security.BookingDirectBookingAuthority;

import java.util.UUID;

public record BookingAuthorizationResponse(
        UUID actorUserId,
        UUID resourceOwnerUserId,
        String resourceOwnerDisplayName,
        BookingDirectBookingAuthority directBookingAuthority,
        CancellationAuthority cancellationAuthority) {
    public BookingAuthorizationResponse(UUID actorUserId, UUID resourceOwnerUserId,
                                        String resourceOwnerDisplayName) {
        this(actorUserId, resourceOwnerUserId, resourceOwnerDisplayName, null, null);
    }

    public BookingAuthorizationResponse(UUID actorUserId, UUID resourceOwnerUserId,
                                        String resourceOwnerDisplayName,
                                        BookingDirectBookingAuthority directBookingAuthority) {
        this(actorUserId, resourceOwnerUserId, resourceOwnerDisplayName, directBookingAuthority, null);
    }

    public enum CancellationAuthority {
        OWNER,
        ADMIN,
        SYSTEM_ADMIN
    }
}
