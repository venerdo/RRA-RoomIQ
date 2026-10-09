package rw.rra.roomiq.scheduling.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AvailabilitySearchResponse(
        UUID workingCalendarId,
        UUID officeBuildingId,
        String timezone,
        Instant searchedAt,
        Instant occupancySnapshotAt,
        boolean bookingOccupancyIncluded,
        boolean bookingConfirmationRequired,
        List<AvailabilityWindow> windows) {
}
