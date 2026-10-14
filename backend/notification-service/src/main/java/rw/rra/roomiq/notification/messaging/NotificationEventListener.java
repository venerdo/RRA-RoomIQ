package rw.rra.roomiq.notification.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import rw.rra.roomiq.notification.domain.event.NotificationEventCodec;
import rw.rra.roomiq.notification.domain.service.NotificationEventProcessingService;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "roomiq.notification.events.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventListener {
    private final NotificationEventCodec codec;
    private final NotificationEventProcessingService processingService;

    public NotificationEventListener(NotificationEventCodec codec,
                                     NotificationEventProcessingService processingService) {
        this.codec = codec;
        this.processingService = processingService;
    }

    @RabbitListener(queues = NotificationEventMessagingConfiguration.QUEUE)
    public void receive(org.springframework.amqp.core.Message message) {
        var event = codec.decode(message.getBody());
        String receivedRoutingKey = message.getMessageProperties().getReceivedRoutingKey();
        if (!codec.routingKeyFor(event.eventType()).equals(receivedRoutingKey)) {
            throw new rw.rra.roomiq.notification.domain.event.InvalidNotificationEventException(
                    "Notification event routing key does not match its event type");
        }
        processingService.process(event);
    }
}
