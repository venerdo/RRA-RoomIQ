package rw.rra.roomiq.organization.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record ProvinceResponse(UUID id, UUID countryId, String name, boolean active,
                               Instant createdAt) {
}