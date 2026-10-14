package rw.rra.roomiq.room.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "room.events.relay.enabled", havingValue = "true", matchIfMissing = true)
public class RoomEventMessagingConfiguration {
    public static final String EVENTS_EXCHANGE = "roomiq.events.v1";
    public static final String STATUS_ROUTING_KEY = "room.status.changed";
    public static final String MAINTENANCE_ROUTING_KEY = "room.maintenance.changed";

    @Bean
    public DirectExchange roomIqEventsExchange() {
        return new DirectExchange(EVENTS_EXCHANGE, true, false);
    }
}
