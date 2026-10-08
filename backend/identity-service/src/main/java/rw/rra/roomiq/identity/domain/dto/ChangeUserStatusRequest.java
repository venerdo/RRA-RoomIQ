package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.identity.domain.entity.UserStatus;

public record ChangeUserStatusRequest(
        @NotNull UserStatus status,
        @Size(max = 500) String reason
) {
}
