package rw.rra.roomiq.organization.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record FloorResponse(UUID id, UUID officeBuildingId, String name, short level,
                            boolean active, Instant createdAt) {
}