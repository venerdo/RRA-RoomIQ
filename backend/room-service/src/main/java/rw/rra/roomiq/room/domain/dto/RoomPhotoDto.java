package rw.rra.roomiq.room.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record RoomPhotoDto(UUID id, UUID roomId, String cloudinaryPublicId, String secureUrl, Short sortOrder,
                           boolean primary, boolean approvedForPublic, Instant createdAt) {
}