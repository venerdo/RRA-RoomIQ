package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.NotNull;

public record OrganizationAuthorizationRequest(@NotNull Action action) {
    public enum Action {
        READ,
        MANAGE
    }
}