package rw.rra.roomiq.room.domain.dto;

import rw.rra.roomiq.room.domain.entity.FacilityState;

import java.time.Instant;
import java.util.UUID;

public record RoomFacilityDto(UUID id, UUID roomId, UUID facilityTypeId, short quantity,
                              FacilityState state, Instant lastServicedAt) {
}