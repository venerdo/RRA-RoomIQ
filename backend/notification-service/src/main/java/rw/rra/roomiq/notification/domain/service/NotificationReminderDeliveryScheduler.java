package rw.rra.roomiq.notification.domain.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "roomiq.notification.reminders.delivery-enabled", havingValue = "true",
        matchIfMissing = true)
public class NotificationReminderDeliveryScheduler {
    private final NotificationEventProcessingService processingService;

    public NotificationReminderDeliveryScheduler(NotificationEventProcessingService processingService) {
        this.processingService = processingService;
    }

    @Scheduled(fixedDelayString = "${roomiq.notification.reminders.fixed-delay-ms:1000}")
    public void deliverDueReminders() {
        processingService.deliverDueReminders();
    }
}
