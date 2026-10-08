package rw.rra.roomiq.organization.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateProvinceRequest(
        @NotNull UUID countryId,
        @NotBlank @Size(max = 150) String name,
        Boolean active
) {
}