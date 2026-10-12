package rw.rra.roomiq.booking.domain.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import rw.rra.roomiq.booking.domain.entity.BookingEventOutboxEntry;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BookingEventOutboxRepositoryPostgresTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("roomiq.booking.event-relay.enabled", () -> "false");
    }

    @Autowired
    private BookingEventOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentWorkersCannotClaimTheSamePendingEvent() throws Exception {
        Instant now = Instant.now();
        UUID rowId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO booking_event_outbox
                    (id, event_id, event_type, event_version, aggregate_type, aggregate_id,
                     aggregate_version, payload, occurred_at, created_at, next_attempt_at)
                VALUES (?, ?, 'booking.occupancy.changed', 1, 'RESERVATION', ?, 1,
                        CAST(? AS jsonb), ?, ?, ?)
                """, rowId, eventId, aggregateId, "{\"eventId\":\"" + eventId + "\"}",
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

        CountDownLatch firstWorkerLockedRow = new CountDownLatch(1);
        CountDownLatch releaseFirstWorker = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<List<BookingEventOutboxEntry>> firstClaim =
                    executor.submit(() -> transaction.execute(status -> {
                        List<BookingEventOutboxEntry> claimed = outboxRepository.claimDue(now);
                        firstWorkerLockedRow.countDown();
                        await(releaseFirstWorker);
                        return claimed;
                    }));
            assertThat(firstWorkerLockedRow.await(10, TimeUnit.SECONDS)).isTrue();

            List<?> secondClaim = transaction.execute(status -> outboxRepository.claimDue(now));

            assertThat(secondClaim).isEmpty();
            releaseFirstWorker.countDown();
            assertThat(firstClaim.get(10, TimeUnit.SECONDS))
                    .extracting(entry -> entry.getEventId())
                    .containsExactly(eventId);
        } finally {
            releaseFirstWorker.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release the outbox row lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding the outbox row lock", exception);
        }
    }
}
