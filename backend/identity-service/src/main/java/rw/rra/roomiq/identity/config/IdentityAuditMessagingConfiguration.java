package rw.rra.roomiq.identity.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityAuditMessagingConfiguration {
    public static final String EVENTS_EXCHANGE = "roomiq.events.v1";
    public static final String AUDIT_QUEUE = "roomiq.audit.identity.v1";
    public static final String AUDIT_ROUTING_KEY = "audit.identity";

    @Bean
    public DirectExchange roomIqEventsExchange() {
        return new DirectExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue identityAuditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).build();
    }

    @Bean
    public Binding identityAuditBinding(Queue identityAuditQueue, DirectExchange roomIqEventsExchange) {
        return BindingBuilder.bind(identityAuditQueue).to(roomIqEventsExchange).with(AUDIT_ROUTING_KEY);
    }
}