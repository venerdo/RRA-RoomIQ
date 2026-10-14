package rw.rra.roomiq.notification.domain.event;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationEvent(
        UUID eventId,
        String sourceService,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String correlationId,
        String causationId,
        Instant occurredAt,
        JsonNode payload,
        List<UUID> recipientUserIds,
        String resourceType,
        UUID resourceId) {
}
