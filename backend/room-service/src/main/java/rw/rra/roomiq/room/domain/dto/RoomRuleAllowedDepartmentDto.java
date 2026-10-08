package rw.rra.roomiq.room.domain.dto;

import java.util.UUID;

public record RoomRuleAllowedDepartmentDto(UUID id, UUID roomRuleId, UUID departmentId) {
}