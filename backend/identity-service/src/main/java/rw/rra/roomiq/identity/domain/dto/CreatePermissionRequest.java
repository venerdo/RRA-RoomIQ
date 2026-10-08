package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_.:]{1,127}") String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 80) String domain
) {
}
