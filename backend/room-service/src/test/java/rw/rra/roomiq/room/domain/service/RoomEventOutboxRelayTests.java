package rw.rra.roomiq.room.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PGobject;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import rw.rra.roomiq.room.config.RoomEventMessagingConfiguration;
import rw.rra.roomiq.room.domain.entity.RoomEventOutboxEntry;
import rw.rra.roomiq.room.domain.repository.RoomEventOutboxRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomEventOutboxRelayTests {
    private static final Instant NOW = Instant.parse("2030-04-10T09:00:00Z");

    @Mock
    private RoomEventOutboxRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RoomEventOutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new RoomEventOutboxRelay(repository, rabbitTemplate, 100, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesPersistentRoomEventAndMarksOnlyConfirmedDelivery() throws SQLException {
        RoomEventOutboxEntry entry = entry(UUID.randomUUID(), "RoomStatusChanged");
        when(repository.claimDue(NOW)).thenReturn(List.of(entry));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, "ack"));
            return null;
        }).when(rabbitTemplate).send(eq(RoomEventMessagingConfiguration.EVENTS_EXCHANGE),
                eq(RoomEventMessagingConfiguration.STATUS_ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        relay.publishPendingEvents();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).send(eq(RoomEventMessagingConfiguration.EVENTS_EXCHANGE),
                eq(RoomEventMessagingConfiguration.STATUS_ROUTING_KEY), messageCaptor.capture(),
                correlationCaptor.capture());
        Message message = messageCaptor.getValue();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(entry.getPayload().getValue());
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(entry.getEventId().toString());
        assertThat(correlationCaptor.getValue().getId()).isEqualTo(entry.getEventId().toString());
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(entry.getPublishedAt()).isEqualTo(NOW);
        assertThat(entry.getAttemptCount()).isZero();
    }

    @Test
    void brokerOutageRetainsEventAndSchedulesBoundedRetry() {
        RoomEventOutboxEntry entry = entry(UUID.randomUUID(), "RoomMaintenanceChanged");
        when(repository.claimDue(NOW)).thenReturn(List.of(entry));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class),
                        any(CorrelationData.class));

        relay.publishPendingEvents();

        assertThat(entry.getPublishedAt()).isNull();
        assertThat(entry.getAttemptCount()).isEqualTo(1);
        assertThat(entry.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(entry.getLastError()).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void brokerNackAndUnroutableConfirmAreNotMarkedPublished() {
        RoomEventOutboxEntry entry = entry(UUID.randomUUID(), "RoomMaintenanceChanged");
        when(repository.claimDue(NOW)).thenReturn(List.of(entry));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312, "NO_ROUTE",
                    RoomEventMessagingConfiguration.EVENTS_EXCHANGE,
                    RoomEventMessagingConfiguration.MAINTENANCE_ROUTING_KEY));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, "ack"));
            return null;
        }).when(rabbitTemplate).send(eq(RoomEventMessagingConfiguration.EVENTS_EXCHANGE),
                eq(RoomEventMessagingConfiguration.MAINTENANCE_ROUTING_KEY), any(Message.class),
                any(CorrelationData.class));

        relay.publishPendingEvents();

        assertThat(entry.getPublishedAt()).isNull();
        assertThat(entry.getAttemptCount()).isEqualTo(1);
        assertThat(entry.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
    }

    private static RoomEventOutboxEntry entry(UUID id, String eventType) {
        try {
            PGobject payload = new PGobject();
            payload.setType("jsonb");
            payload.setValue("{\"eventId\":\"" + id + "\"}");
            return new RoomEventOutboxEntry(id, eventType, 1, "ROOM", UUID.randomUUID(), 1,
                    payload, NOW, NOW);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
