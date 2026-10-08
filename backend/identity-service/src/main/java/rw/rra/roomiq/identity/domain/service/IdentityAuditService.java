package rw.rra.roomiq.identity.domain.service;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.CorrelationIdFilter;
import rw.rra.roomiq.identity.domain.dto.IdentityAuditEvent;
import rw.rra.roomiq.identity.domain.entity.IdentityAuditOutboxEntry;
import rw.rra.roomiq.identity.domain.repository.IdentityAuditOutboxRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class IdentityAuditService {
    private final IdentityAuditOutboxRepository outboxRepository;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public IdentityAuditService(IdentityAuditOutboxRepository outboxRepository,
                                tools.jackson.databind.ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String eventType, UUID actorUserId, String resourceType, UUID resourceId,
                       UUID scopeOfficeBuildingId, String outcome) {
        record(eventType, actorUserId, resourceType, resourceId, scopeOfficeBuildingId, outcome, Map.of());
        }

        @Transactional
        public void record(String eventType, UUID actorUserId, String resourceType, UUID resourceId,
                   UUID scopeOfficeBuildingId, String outcome, Map<String, Object> metadata) {
        Instant now = Instant.now();
        IdentityAuditEvent event = new IdentityAuditEvent(UUID.randomUUID(), 1, eventType, now,
                "identity-service", actorUserId, resourceType, resourceId, scopeOfficeBuildingId,
            outcome, MDC.get(CorrelationIdFilter.MDC_KEY), Map.copyOf(metadata));
        try {
            outboxRepository.save(new IdentityAuditOutboxEntry(event.eventId(), event.eventType(),
                    objectMapper.writeValueAsString(event), now));
        } catch (Exception exception) {
            throw new IllegalStateException("Identity audit event could not be recorded", exception);
        }
    }

    @Transactional
    public void recordCurrentActor(String eventType, String resourceType, UUID resourceId,
                                   UUID scopeOfficeBuildingId, String outcome) {
        record(eventType, currentActorId(), resourceType, resourceId, scopeOfficeBuildingId, outcome);
    }

    @Transactional
    public void recordCurrentActor(String eventType, String resourceType, UUID resourceId,
                                   UUID scopeOfficeBuildingId, String outcome, Map<String, Object> metadata) {
        record(eventType, currentActorId(), resourceType, resourceId, scopeOfficeBuildingId, outcome, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuthenticationFailure() {
        record("AUTHENTICATION_FAILED", null, "AUTHENTICATION", null, null, "DENIED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefreshFailure() {
        record("REFRESH_TOKEN_REJECTED", null, "AUTHENTICATION", null, null, "DENIED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuthorizationDenied() {
        record("AUTHORIZATION_DENIED", currentActorId(), "API", null, null, "DENIED");
    }

    private UUID currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            try {
                return UUID.fromString(jwtAuthentication.getToken().getSubject());
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return null;
    }
}