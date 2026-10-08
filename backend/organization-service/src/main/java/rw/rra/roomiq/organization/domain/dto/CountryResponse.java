package rw.rra.roomiq.organization.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record CountryResponse(UUID id, String name, String isoCode, boolean active,
                              Instant createdAt, Instant updatedAt) {
}