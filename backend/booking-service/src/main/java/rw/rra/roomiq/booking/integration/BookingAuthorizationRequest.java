package rw.rra.roomiq.booking.integration;

import java.util.UUID;

public record BookingAuthorizationRequest(
        Action action,
        UUID buildingId,
        UUID departmentId,
        UUID resourceOwnerUserId,
        Boolean vipRoom) {

    public enum Action {
        AUTHENTICATE,
        REQUEST_CREATE,
        DIRECT_CREATE,
        APPROVE,
        CANCEL,
        EXTENSION_REQUEST,
        EXTENSION_DECIDE
    }
}
