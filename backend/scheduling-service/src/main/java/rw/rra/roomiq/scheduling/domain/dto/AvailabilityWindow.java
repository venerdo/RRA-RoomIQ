package rw.rra.roomiq.scheduling.domain.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityWindow(
        UUID roomId,
        String roomName,
        String roomCode,
        int capacity,
        LocalDate occurrenceDate,
        Instant startsAt,
        Instant endsAt,
        Integer minimumDurationMinutes,
        Integer maximumDurationMinutes,
        UUID recurrenceRuleId) {
}
