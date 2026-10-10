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
        REQUEST_SUBMIT,
        REQUEST_LIST,
        REQUEST_READ,
        DIRECT_CREATE,
        APPROVE,
        RESERVATION_LIFECYCLE,
        CANCEL,
        EXTENSION_REQUEST,
        EXTENSION_DECIDE
    }
}
