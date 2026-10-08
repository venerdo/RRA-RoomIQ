package rw.rra.roomiq.organization.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.organization.domain.validation.IanaTimezone;

import java.util.UUID;

public record CreateOfficeBuildingRequest(
        @NotNull UUID districtId,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 64) String code,
        String address,
        @Size(max = 64) @IanaTimezone String timezone,
        UUID workingCalendarId,
        Boolean active
) {
}