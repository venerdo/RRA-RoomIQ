package rw.rra.roomiq.room.domain.dto;

import java.util.UUID;

public record RoomTypeDto(UUID id, String code, String name, boolean active) {
}