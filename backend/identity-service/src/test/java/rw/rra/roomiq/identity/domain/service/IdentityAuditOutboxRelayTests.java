package rw.rra.roomiq.identity.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import rw.rra.roomiq.identity.config.IdentityAuditMessagingConfiguration;
import rw.rra.roomiq.identity.domain.entity.IdentityAuditOutboxEntry;
import rw.rra.roomiq.identity.domain.repository.IdentityAuditOutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAuditOutboxRelayTests {
    @Mock
    private IdentityAuditOutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

        private IdentityAuditOutboxRelay relay;

        @BeforeEach
        void setUp() {
                relay = new IdentityAuditOutboxRelay(outboxRepository, rabbitTemplate, 3000);
        }

    @Test
    void publishesPersistentJsonMessageAndMarksOutboxEntryPublished() {
        Instant createdAt = Instant.now().minusSeconds(5);
        UUID eventId = UUID.randomUUID();
        IdentityAuditOutboxEntry entry = new IdentityAuditOutboxEntry(eventId,
                "IDENTITY_USER_CREATED", "{\"eventId\":\"" + eventId + "\"}", createdAt);
        when(outboxRepository.findTop50ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(entry));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, ""));
            return null;
        }).when(rabbitTemplate).send(eq(IdentityAuditMessagingConfiguration.EVENTS_EXCHANGE),
                eq(IdentityAuditMessagingConfiguration.AUDIT_ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        relay.publishPendingEvents();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(IdentityAuditMessagingConfiguration.EVENTS_EXCHANGE),
                eq(IdentityAuditMessagingConfiguration.AUDIT_ROUTING_KEY), messageCaptor.capture(),
                any(CorrelationData.class));
        Message message = messageCaptor.getValue();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(entry.getPublishedAt()).isNotNull();
        assertThat(entry.getAttemptCount()).isZero();
    }

    @Test
    void defersFailedDeliveryWithoutLosingTheOutboxEvent() {
        Instant createdAt = Instant.now().minusSeconds(5);
        IdentityAuditOutboxEntry entry = new IdentityAuditOutboxEntry(UUID.randomUUID(),
                "AUTHENTICATION_FAILED", "{}", createdAt);
        when(outboxRepository.findTop50ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(entry));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        relay.publishPendingEvents();

        assertThat(entry.getPublishedAt()).isNull();
        assertThat(entry.getAttemptCount()).isEqualTo(1);
        assertThat(entry.getNextAttemptAt()).isAfter(Instant.now());
    }
}