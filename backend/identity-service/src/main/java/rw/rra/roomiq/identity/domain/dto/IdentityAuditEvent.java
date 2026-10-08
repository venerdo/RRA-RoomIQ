package rw.rra.roomiq.identity.domain.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IdentityAuditEvent(
        UUID eventId,
        int schemaVersion,
        String eventType,
        Instant timestamp,
        String sourceService,
        UUID actorUserId,
        String resourceType,
        UUID resourceId,
        UUID scopeOfficeBuildingId,
        String outcome,
        String correlationId,
        Map<String, Object> metadata
) {
}