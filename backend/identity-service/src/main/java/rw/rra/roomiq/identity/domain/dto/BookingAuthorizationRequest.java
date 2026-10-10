package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookingAuthorizationRequest(
        @NotNull Action action,
        UUID buildingId,
        UUID departmentId,
        UUID resourceOwnerUserId,
        Boolean vipRoom) {

    @AssertTrue(message = "Authorization facts do not match the requested action")
    public boolean isValidForAction() {
        if (action == null) {
            return true;
        }
        return switch (action) {
            case AUTHENTICATE -> buildingId == null && departmentId == null
                    && resourceOwnerUserId == null && vipRoom == null;
            case REQUEST_CREATE -> buildingId != null && departmentId != null && vipRoom != null
                    && resourceOwnerUserId == null;
            case REQUEST_SUBMIT -> buildingId != null && departmentId != null && vipRoom != null
                    && resourceOwnerUserId != null;
            case REQUEST_LIST -> departmentId == null && resourceOwnerUserId == null && vipRoom == null;
            case REQUEST_READ -> buildingId != null && resourceOwnerUserId != null
                    && departmentId == null && vipRoom == null;
            case DIRECT_CREATE -> buildingId != null && departmentId != null
                    && resourceOwnerUserId == null && vipRoom != null;
            case RESERVATION_LIFECYCLE -> buildingId != null && departmentId != null
                    && resourceOwnerUserId != null && vipRoom == null;
            case APPROVE, CANCEL, EXTENSION_REQUEST, EXTENSION_DECIDE ->
                    buildingId != null && resourceOwnerUserId != null
                            && departmentId == null && vipRoom == null;
        };
    }

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
