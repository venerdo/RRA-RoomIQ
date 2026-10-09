package rw.rra.roomiq.booking.domain.dto;

import jakarta.validation.constraints.NotNull;
import rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType;

public record BookingDecisionRequest(
        @NotNull ApprovalDecisionType decision,
        String comment) {
}
