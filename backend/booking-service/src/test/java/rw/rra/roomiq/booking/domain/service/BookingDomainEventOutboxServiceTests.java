package rw.rra.roomiq.booking.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;
import rw.rra.roomiq.booking.domain.entity.BookingEventOutboxEntry;
import rw.rra.roomiq.booking.domain.repository.BookingEventOutboxRepository;
import rw.rra.roomiq.common.web.CorrelationIdFilter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingDomainEventOutboxServiceTests {
    private static final Instant NOW = Instant.parse("2030-04-10T09:00:00Z");

    @Mock
    private BookingEventOutboxRepository repository;

    @Test
    void persistsVersionedBookingEnvelopeAndTrustedRecipientFacts() {
        BookingDomainEventOutboxService service = new BookingDomainEventOutboxService(repository,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        UUID requestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        MDC.put(CorrelationIdFilter.MDC_KEY, "request-correlation-17");
        try {
            UUID eventId = service.record("BookingRequested", "BOOKING_REQUEST", requestId, 2, NOW,
                    List.of(requesterId, requesterId),
                    Map.of("requesterUserId", requesterId.toString(), "requestReference", "BR-17"));

            ArgumentCaptor<BookingEventOutboxEntry> entryCaptor =
                    ArgumentCaptor.forClass(BookingEventOutboxEntry.class);
            verify(repository).save(entryCaptor.capture());
            BookingEventOutboxEntry entry = entryCaptor.getValue();
            assertThat(entry.getEventId()).isEqualTo(eventId);
            assertThat(entry.getEventType()).isEqualTo("BookingRequested");
            assertThat(entry.getAggregateType()).isEqualTo("BOOKING_REQUEST");
            assertThat(entry.getAggregateId()).isEqualTo(requestId);
            assertThat(entry.getAggregateVersion()).isEqualTo(2);
            assertThat(entry.getPayload().getValue())
                    .contains("\"sourceService\":\"booking-service\"")
                    .contains("\"eventVersion\":1")
                    .contains("\"correlationId\":\"request-correlation-17\"")
                    .contains("\"recipientUserIds\":[\"" + requesterId + "\"]")
                    .contains("\"requesterUserId\":\"" + requesterId + "\"");
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
