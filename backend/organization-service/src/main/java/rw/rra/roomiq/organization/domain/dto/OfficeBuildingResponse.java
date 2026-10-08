package rw.rra.roomiq.organization.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record OfficeBuildingResponse(UUID id, UUID districtId, String name, String code,
                                    String address, String timezone, UUID workingCalendarId,
                                    boolean active, Instant createdAt) {
}