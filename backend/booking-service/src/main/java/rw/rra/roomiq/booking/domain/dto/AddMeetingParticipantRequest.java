package rw.rra.roomiq.booking.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.booking.domain.enums.ParticipantRole;

import java.util.UUID;

public record AddMeetingParticipantRequest(
        UUID userId,
        @Email @Size(max = 320) String externalEmail,
        @Size(max = 200) String displayName,
        @NotNull ParticipantRole role) {

    @AssertTrue(message = "Provide either an internal user ID or an external email and display name; organizer is immutable")
    public boolean isParticipantInputValid() {
        if (role == null || role == ParticipantRole.ORGANIZER) {
            return false;
        }
        if (userId != null) {
            return externalEmail == null && displayName == null;
        }
        return externalEmail != null && !externalEmail.isBlank()
                && displayName != null && !displayName.isBlank();
    }
}
