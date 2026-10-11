package rw.rra.roomiq.booking.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType;

public record BookingExtensionDecisionRequest(
        @NotNull ApprovalDecisionType decision,
        @Size(max = 1000) String comment) {
}
