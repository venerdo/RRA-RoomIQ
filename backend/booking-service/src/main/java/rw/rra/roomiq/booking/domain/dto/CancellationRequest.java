package rw.rra.roomiq.booking.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.booking.domain.enums.CancellationReason;

public record CancellationRequest(
        @NotNull CancellationReason reasonCode,
        @NotBlank @Size(max = 2000) String reason,
        @Size(max = 1000) String overrideReason) {
}
