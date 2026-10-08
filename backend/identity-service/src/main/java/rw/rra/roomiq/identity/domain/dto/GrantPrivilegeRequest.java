package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record GrantPrivilegeRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,127}") String privilegeCode,
        UUID grantedByUserId,
        Instant validFrom,
        Instant validTo
) {
}
