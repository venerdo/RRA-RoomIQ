package rw.rra.roomiq.booking.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateBookingRequest(
        @NotNull UUID departmentId,
        @NotNull UUID roomId,
        @NotNull UUID officeBuildingId,
        UUID recurrenceRuleId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String purpose,
        @NotNull Instant requestedStart,
        @NotNull Instant requestedEnd,
        @Min(1) int attendeeCount,
        Boolean externalGuests) {
    @AssertTrue(message = "requestedEnd must be after requestedStart")
    public boolean isValidInterval() {
        return requestedStart == null || requestedEnd == null || requestedEnd.isAfter(requestedStart);
    }
}
