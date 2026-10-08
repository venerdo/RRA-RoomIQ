package rw.rra.roomiq.organization.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record DistrictResponse(UUID id, UUID provinceId, String name, boolean active,
                               Instant createdAt) {
}