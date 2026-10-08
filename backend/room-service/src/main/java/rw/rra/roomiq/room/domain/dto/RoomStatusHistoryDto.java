package rw.rra.roomiq.room.domain.dto;

import rw.rra.roomiq.room.domain.entity.RoomStatus;

import java.time.Instant;
import java.util.UUID;

public record RoomStatusHistoryDto(UUID id, UUID roomId, RoomStatus oldStatus, RoomStatus newStatus,
                                   UUID changedByUserId, String reason, Instant changedAt) {
}