package rw.rra.roomiq.room.domain.dto;

import java.util.UUID;

public record MaintenancePeriodDto(UUID id, UUID roomId, String period, String reason, UUID createdByUserId) {
}