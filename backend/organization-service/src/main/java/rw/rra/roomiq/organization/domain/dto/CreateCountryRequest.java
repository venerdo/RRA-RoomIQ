package rw.rra.roomiq.organization.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCountryRequest(
        @NotBlank @Size(max = 150) String name,
        @Pattern(regexp = "(?i)^\\s*$|^[A-Z]{2}$", message = "must be blank or a two-letter ISO country code")
        String isoCode,
        Boolean active
) {
}