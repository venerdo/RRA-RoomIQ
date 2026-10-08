package rw.rra.roomiq.room.domain.dto;

import java.util.UUID;

public record FacilityTypeDto(UUID id, String code, String name, String category, boolean active) {
}