package rw.rra.roomiq.booking.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "roomiq.booking.event-relay.enabled", havingValue = "true", matchIfMissing = true)
public class BookingEventMessagingConfiguration {
    public static final String EVENTS_EXCHANGE = "roomiq.events.v1";
    public static final String OCCUPANCY_ROUTING_KEY = "booking.occupancy.changed";

    @Bean
    public DirectExchange roomIqEventsExchange() {
        return new DirectExchange(EVENTS_EXCHANGE, true, false);
    }
}
