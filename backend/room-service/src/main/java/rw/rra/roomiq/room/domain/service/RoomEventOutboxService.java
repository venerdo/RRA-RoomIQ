package rw.rra.roomiq.room.domain.service;

import org.postgresql.util.PGobject;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import rw.rra.roomiq.common.web.CorrelationIdFilter;
import rw.rra.roomiq.room.domain.entity.RoomEventOutboxEntry;
import rw.rra.roomiq.room.domain.repository.RoomEventOutboxRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoomEventOutboxService {
    private static final int EVENT_VERSION = 1;
    private static final String AGGREGATE_TYPE = "ROOM";

    private final RoomEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public RoomEventOutboxService(RoomEventOutboxRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, Clock.systemUTC());
    }

    RoomEventOutboxService(RoomEventOutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStatusChanged(UUID roomId, long aggregateVersion, String status, Instant occurredAt,
                                    List<UUID> recipients) {
        record(roomId, aggregateVersion, "RoomStatusChanged", occurredAt,
                Map.of("recipientUserIds", recipientIds(recipients), "roomId", roomId.toString(),
                        "status", status));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMaintenanceChanged(UUID roomId, long aggregateVersion, UUID maintenancePeriodId,
                                         String action, Instant startsAt, Instant endsAt, Instant occurredAt,
                                         List<UUID> recipients) {
        record(roomId, aggregateVersion, "RoomMaintenanceChanged", occurredAt,
                Map.of("recipientUserIds", recipientIds(recipients), "roomId", roomId.toString(),
                        "maintenancePeriodId", maintenancePeriodId.toString(), "action", action,
                        "periodStart", startsAt.toString(), "periodEnd", endsAt.toString()));
    }

    private static List<String> recipientIds(List<UUID> recipients) {
        if (recipients == null || recipients.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Room events require validated recipient identities");
        }
        return recipients.stream().distinct().map(UUID::toString).toList();
    }

    private void record(UUID roomId, long aggregateVersion, String eventType, Instant occurredAt,
                        Map<String, Object> payload) {
        if (roomId == null || aggregateVersion < 0 || occurredAt == null) {
            throw new IllegalArgumentException("Room events require an aggregate identity, version, and timestamp");
        }
        UUID eventId = UUID.randomUUID();
        RoomDomainEvent event = new RoomDomainEvent(eventId, "room-service", eventType, EVENT_VERSION,
                AGGREGATE_TYPE, roomId, aggregateVersion, MDC.get(CorrelationIdFilter.MDC_KEY),
                null, occurredAt, payload);
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize Room domain event", exception);
        }

        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        try {
            jsonb.setValue(serialized);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not map Room domain event to PostgreSQL JSONB", exception);
        }
        repository.save(new RoomEventOutboxEntry(eventId, eventType, EVENT_VERSION, AGGREGATE_TYPE,
                roomId, aggregateVersion, jsonb, occurredAt, clock.instant()));
    }

    private record RoomDomainEvent(UUID eventId, String sourceService, String eventType, int eventVersion,
                                   String aggregateType, UUID aggregateId, long aggregateVersion,
                                   String correlationId, String causationId, Instant occurredAt,
                                   Map<String, Object> payload) {
    }
}
