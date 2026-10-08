package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 64) String nationalId,
        @Size(max = 32) String phone,
        @Pattern(regexp = "^(?=.{12,72}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])[\\x21-\\x7E]+$",
                message = "password must be 12-72 printable ASCII characters with lowercase, uppercase, number, and symbol") String password,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 200) String displayName,
        UUID departmentId,
        UUID officeBuildingId
) {
}
