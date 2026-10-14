package rw.rra.roomiq.notification.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import rw.rra.roomiq.notification.domain.entity.Notification;
import rw.rra.roomiq.notification.domain.entity.NotificationEventReceipt;
import rw.rra.roomiq.notification.domain.event.InvalidNotificationEventException;
import rw.rra.roomiq.notification.domain.event.NotificationEvent;
import rw.rra.roomiq.notification.domain.repository.NotificationEventReceiptRepository;
import rw.rra.roomiq.notification.domain.repository.NotificationRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationEventProcessingService {
    private static final String RESERVATION_RESOURCE = "RESERVATION";
    private static final Set<String> TERMINAL_EVENT_TYPES = Set.of(
            "BookingCancelled", "MeetingCompleted", "MeetingReleased", "AutomaticReleaseOrNoShow");
    private static final int REMINDER_BATCH_SIZE = 100;

    private final NotificationEventReceiptRepository receiptRepository;
    private final NotificationRepository notificationRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public NotificationEventProcessingService(NotificationEventReceiptRepository receiptRepository,
                                              NotificationRepository notificationRepository,
                                              EntityManager entityManager) {
        this(receiptRepository, notificationRepository, entityManager, Clock.systemUTC());
    }

    NotificationEventProcessingService(NotificationEventReceiptRepository receiptRepository,
                                       NotificationRepository notificationRepository, EntityManager entityManager,
                                       Clock clock) {
        this.receiptRepository = receiptRepository;
        this.notificationRepository = notificationRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public ProcessingResult process(NotificationEvent event) {
        if (isReservationReminder(event) || isTerminalForReservation(event)) {
            lockReservation(event.resourceId());
        }
        int inserted = receiptRepository.insertIfAbsent(UUID.randomUUID(), event.eventId(),
                event.sourceService(), event.eventType(), event.eventVersion(), event.aggregateType(),
                event.aggregateId(), event.aggregateVersion(), event.correlationId(), event.causationId(),
                event.resourceType(), event.resourceId(), clock.instant());
        if (inserted == 0) {
            NotificationEventReceipt existing = receiptRepository.findByEventId(event.eventId())
                    .orElseThrow(() -> new IllegalStateException("Event receipt disappeared after deduplication"));
            if (!existing.matches(toReceipt(event))) {
                throw new InvalidNotificationEventException(
                        "Notification event identifier was reused with different event metadata");
            }
            return ProcessingResult.DUPLICATE;
        }

        Instant now = clock.instant();
        if (isTerminalForReservation(event)) {
            notificationRepository.cancelScheduledReminders(
                    RESERVATION_RESOURCE, event.resourceId(), now);
        }
        if (isReservationReminder(event) && hasTerminalReservationEvent(event.resourceId())) {
            return ProcessingResult.PROCESSED_WITHOUT_NOTIFICATIONS;
        }
        if (isReservationReminder(event)) {
            Instant scheduledAt = Instant.parse(event.payload().get("reminderAt").asText());
            for (UUID recipient : event.recipientUserIds()) {
                notificationRepository.insertScheduledReminderIfAbsent(
                        UUID.randomUUID(), recipient, RESERVATION_RESOURCE, event.resourceId(),
                        "IN_APP", null, scheduledAt, event.occurredAt());
            }
            return event.recipientUserIds().isEmpty() ? ProcessingResult.PROCESSED_WITHOUT_NOTIFICATIONS
                    : ProcessingResult.PROCESSED;
        }

        List<Notification> notifications = event.recipientUserIds().stream()
                .map(recipient -> new Notification(UUID.randomUUID(), recipient, event.eventType(),
                        event.resourceType(), event.resourceId(), now))
                .toList();
        notificationRepository.saveAll(notifications);
        return notifications.isEmpty() ? ProcessingResult.PROCESSED_WITHOUT_NOTIFICATIONS
                : ProcessingResult.PROCESSED;
    }

    @Transactional
    public int deliverDueReminders() {
        Instant now = clock.instant();
        List<UUID> resourceIds = notificationRepository.findDueReminderResourceIds(
                RESERVATION_RESOURCE, now, REMINDER_BATCH_SIZE);
        int delivered = 0;
        for (UUID resourceId : resourceIds) {
            lockReservation(resourceId);
            List<Notification> due = notificationRepository.findDueRemindersForUpdate(
                    RESERVATION_RESOURCE, resourceId, now);
            if (hasTerminalReservationEvent(resourceId)) {
                due.forEach(notification -> notification.cancel(now));
            } else {
                due.forEach(Notification::makePending);
                delivered += due.size();
            }
            notificationRepository.saveAllAndFlush(due);
        }
        return delivered;
    }

    private boolean hasTerminalReservationEvent(UUID reservationId) {
        return receiptRepository.existsByResourceTypeAndResourceIdAndEventTypeIn(
                RESERVATION_RESOURCE, reservationId, TERMINAL_EVENT_TYPES);
    }

    private void lockReservation(UUID reservationId) {
        entityManager.createNativeQuery("""
                SELECT pg_advisory_xact_lock(hashtextextended(CAST(:reservationId AS text), 0))
                """)
                .setParameter("reservationId", reservationId.toString())
                .getSingleResult();
    }

    private static boolean isReservationReminder(NotificationEvent event) {
        return "Reminder".equals(event.eventType())
                && RESERVATION_RESOURCE.equals(event.resourceType())
                && event.resourceId() != null;
    }

    private static boolean isTerminalForReservation(NotificationEvent event) {
        return TERMINAL_EVENT_TYPES.contains(event.eventType())
                && RESERVATION_RESOURCE.equals(event.resourceType())
                && event.resourceId() != null;
    }

    private NotificationEventReceipt toReceipt(NotificationEvent event) {
        return new NotificationEventReceipt(UUID.randomUUID(), event.eventId(), event.sourceService(),
                event.eventType(), event.eventVersion(), event.aggregateType(), event.aggregateId(),
                event.aggregateVersion(), event.correlationId(), event.causationId(),
                event.resourceType(), event.resourceId(), clock.instant());
    }

    public enum ProcessingResult {
        PROCESSED,
        PROCESSED_WITHOUT_NOTIFICATIONS,
        DUPLICATE
    }
}
