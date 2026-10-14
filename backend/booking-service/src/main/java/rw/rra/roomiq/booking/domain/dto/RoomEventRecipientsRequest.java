package rw.rra.roomiq.booking.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RoomEventRecipientsRequest(
        @NotNull UUID roomId,
        @NotNull UUID officeBuildingId,
        @NotNull Instant startsAt,
        Instant endsAt,
        @NotNull String contractVersion) {

    @AssertTrue(message = "contractVersion must be v1 and endsAt must be after startsAt when provided")
    public boolean isContractValid() {
        return "v1".equals(contractVersion)
                && (startsAt == null || endsAt == null || endsAt.isAfter(startsAt));
    }
}
