package rw.rra.roomiq.room.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomRuleDto(UUID id, UUID roomId, UUID officeBuildingId, Integer minDurationMinutes,
                          Integer maxDurationMinutes, Integer minAdvanceMinutes, Integer maxAdvanceDays,
                          Integer cancellationDeadlineMinutes, boolean recurringAllowed,
                          boolean externalGuestsAllowed, boolean approvalRequired, boolean outsideHoursAllowed,
                          int releaseBufferMinutes, boolean active, Instant effectiveFrom,
                          List<UUID> allowedDepartmentIds) {
}