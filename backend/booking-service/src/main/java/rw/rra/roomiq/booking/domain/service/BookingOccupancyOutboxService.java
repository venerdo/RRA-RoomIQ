package rw.rra.roomiq.booking.domain.service;

import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancyChangedEvent;
import rw.rra.roomiq.booking.domain.entity.BookingEventOutboxEntry;
import rw.rra.roomiq.booking.domain.entity.Reservation;
import rw.rra.roomiq.booking.domain.entity.ReservationOccurrence;
import rw.rra.roomiq.booking.domain.repository.BookingEventOutboxRepository;

import java.time.Clock;
import java.time.Instant;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class BookingOccupancyOutboxService {
    public static final String EVENT_TYPE = "booking.occupancy.changed";
    public static final String AGGREGATE_TYPE = "RESERVATION";
    private static final int EVENT_VERSION = 1;

    private final BookingEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public BookingOccupancyOutboxService(BookingEventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this(outboxRepository, objectMapper, Clock.systemUTC());
    }

    BookingOccupancyOutboxService(BookingEventOutboxRepository outboxRepository, ObjectMapper objectMapper,
                                  Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUpserts(Reservation reservation, List<ReservationOccurrence> occurrences, Instant occurredAt) {
        if (reservation == null || occurrences == null || occurrences.isEmpty() || occurredAt == null) {
            throw new IllegalArgumentException("Reservation occupancy events require a reservation and occurrences");
        }
        occurrences.stream().sorted(Comparator.comparing(ReservationOccurrence::getStartAt))
                .forEach(occurrence -> record(reservation, occurrence, BookingOccupancyChangedEvent.Action.UPSERT,
                        new BookingOccupancyChangedEvent.OccupiedInterval(occurrence.getStartAt(),
                                occurrence.getEndAt().plusSeconds(
                                        reservation.getReleaseBufferMinutes() * 60L)), occurredAt));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRemovals(Reservation reservation, List<ReservationOccurrence> occurrences,
                               Instant occurredAt) {
        if (reservation == null || occurrences == null || occurredAt == null) {
            throw new IllegalArgumentException("Reservation occupancy removal events require a reservation");
        }
        occurrences.stream().filter(ReservationOccurrence::isOccupancyActive)
                .sorted(Comparator.comparing(ReservationOccurrence::getStartAt))
                .forEach(occurrence -> record(reservation, occurrence, BookingOccupancyChangedEvent.Action.REMOVE,
                        null, occurredAt));
    }

    private void record(Reservation reservation, ReservationOccurrence occurrence,
                        BookingOccupancyChangedEvent.Action action,
                        BookingOccupancyChangedEvent.OccupiedInterval interval, Instant occurredAt) {
        if (!reservation.getId().equals(occurrence.getReservation().getId())) {
            throw new IllegalArgumentException("Occupancy event occurrence belongs to a different reservation");
        }
        UUID eventId = UUID.randomUUID();
        BookingOccupancyChangedEvent event = new BookingOccupancyChangedEvent(eventId, EVENT_VERSION,
                reservation.getId(), occurrence.getRoomId(), action, reservation.getVersion(), occurredAt, interval);
        String serializedEvent;
        try {
            serializedEvent = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize booking occupancy event", exception);
        }
        PGobject payload = new PGobject();
        payload.setType("jsonb");
        try {
            payload.setValue(serializedEvent);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not map booking occupancy event to PostgreSQL JSONB", exception);
        }
        outboxRepository.save(new BookingEventOutboxEntry(eventId, EVENT_TYPE, EVENT_VERSION, AGGREGATE_TYPE,
                reservation.getId(), reservation.getVersion(), payload, occurredAt, clock.instant()));
    }
}
