package rw.rra.roomiq.identity.domain.dto;

import rw.rra.roomiq.identity.domain.security.BookingDirectBookingAuthority;

import java.util.UUID;

public record BookingAuthorizationResponse(
        UUID actorUserId,
        UUID resourceOwnerUserId,
        String resourceOwnerDisplayName,
        BookingDirectBookingAuthority directBookingAuthority) {
    public BookingAuthorizationResponse(UUID actorUserId, UUID resourceOwnerUserId,
                                        String resourceOwnerDisplayName) {
        this(actorUserId, resourceOwnerUserId, resourceOwnerDisplayName, null);
    }
}
