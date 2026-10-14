package rw.rra.roomiq.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "roomiq.notification.events.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventMessagingConfiguration {
    public static final String EVENTS_EXCHANGE = "roomiq.events.v1";
    public static final String QUEUE = "roomiq.notifications.events.v1";
    public static final String DEAD_LETTER_EXCHANGE = "roomiq.notifications.events.dlx.v1";
    public static final String DEAD_LETTER_QUEUE = "roomiq.notifications.events.dead-letter.v1";
    private static final String DEAD_LETTER_ROUTING_KEY = "notification.events.dead-letter.v1";
    private static final List<String> ROUTING_KEYS = List.of(
            "booking.requested", "booking.approved", "booking.rejected", "booking.cancelled",
            "booking.extension.requested", "booking.extension.decided", "room.status.changed",
            "room.maintenance.changed",
            "booking.meeting.completed", "booking.meeting.released", "notification.reminder",
            "booking.automatic-release-or-no-show");

    @Bean
    public DirectExchange roomIqEventsExchange() {
        return new DirectExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationEventsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue,
                                                 DirectExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(notificationDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Declarables notificationEventBindings(Queue notificationEventsQueue,
                                                 DirectExchange roomIqEventsExchange) {
        List<Binding> bindings = ROUTING_KEYS.stream()
                .map(routingKey -> BindingBuilder.bind(notificationEventsQueue).to(roomIqEventsExchange)
                        .with(routingKey))
                .toList();
        return new Declarables(bindings);
    }
}
