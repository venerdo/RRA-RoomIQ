package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MeetingAuthorizationRequest(
        @NotNull Action action,
        @NotNull UUID buildingId,
        @NotNull UUID departmentId,
        @NotNull UUID organizerUserId,
        @NotNull Visibility visibility,
        UUID participantUserId) {

    @AssertTrue(message = "Authorization facts do not match the requested meeting action")
    public boolean isValidForAction() {
        return action == null || action == Action.READ && participantUserId == null
                || action == Action.MANAGE;
    }

    public enum Action {
        READ,
        MANAGE
    }

    public enum Visibility {
        PRIVATE,
        INTERNAL,
        PUBLIC
    }
}
