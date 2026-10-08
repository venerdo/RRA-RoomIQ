package rw.rra.roomiq.room.domain.dto;

import rw.rra.roomiq.room.domain.entity.RoomClass;
import rw.rra.roomiq.room.domain.entity.RoomStatus;

import java.time.Instant;
import java.util.UUID;

public record RoomDto(UUID id, UUID floorId, UUID officeBuildingId, UUID roomTypeId, String name, String code,
                      String description, int capacity, RoomClass roomClass, RoomStatus status, Integer version,
                      Instant createdAt, Instant deletedAt) {
}