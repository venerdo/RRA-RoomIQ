package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
                Boolean system
) {
        public CreateRoleRequest {
                system = Boolean.TRUE.equals(system);
        }
}
