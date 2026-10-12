package rw.rra.roomiq.booking.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancyRequest;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancySnapshotResponse;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancySnapshotResponse.OccupiedInterval;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancySnapshotResponse.RoomOccupancy;
import rw.rra.roomiq.booking.domain.repository.BookingOccupancyRepository;
import rw.rra.roomiq.booking.domain.repository.BookingOccupancyRepository.OccupancyInterval;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingOccupancyService {
    private static final Logger log = LoggerFactory.getLogger(BookingOccupancyService.class);
    private final BookingOccupancyRepository repository;
    private final Clock clock;

    @Autowired
    public BookingOccupancyService(BookingOccupancyRepository repository) {
        this(repository, Clock.systemUTC());
    }

    BookingOccupancyService(BookingOccupancyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BookingOccupancySnapshotResponse snapshot(BookingOccupancyRequest request) {
        validate(request);
        Instant snapshotAt = clock.instant();
        List<OccupancyInterval> intervals;
        try {
            intervals = repository.findActiveIntervals(request.roomIds(), request.startsAt(), request.endsAt());
        } catch (DataAccessException exception) {
            log.error("Booking occupancy snapshot query failed failureType={}",
                    exception.getClass().getSimpleName(), exception);
            throw unavailable();
        }
        Map<UUID, List<OccupiedInterval>> intervalsByRoom = new LinkedHashMap<>();
        request.roomIds().forEach(roomId -> intervalsByRoom.put(roomId, new ArrayList<>()));
        for (OccupancyInterval interval : intervals) {
            if (!intervalsByRoom.containsKey(interval.roomId())
                    || interval.empty()
                    || !interval.lowerInclusive()
                    || interval.upperInclusive()
                    || interval.startsAt() == null
                    || interval.endsAt() == null
                    || !interval.endsAt().isAfter(interval.startsAt())) {
                throw unavailable();
            }
            intervalsByRoom.get(interval.roomId()).add(
                    new OccupiedInterval(interval.startsAt(), interval.endsAt()));
        }
        List<RoomOccupancy> rooms = intervalsByRoom.entrySet().stream()
                .map(entry -> new RoomOccupancy(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new BookingOccupancySnapshotResponse(snapshotAt, rooms);
    }

    private static void validate(BookingOccupancyRequest request) {
        if (request == null || request.roomIds() == null || request.roomIds().isEmpty()
                || request.roomIds().size() > 5000
                || request.roomIds().stream().anyMatch(roomId -> roomId == null)
                || request.roomIds().stream().distinct().count() != request.roomIds().size()
                || request.startsAt() == null || request.endsAt() == null
                || !request.endsAt().isAfter(request.startsAt())
                || !"v1".equals(request.contractVersion())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "BOOKING_OCCUPANCY_REQUEST_INVALID",
                    "The versioned occupancy request is invalid");
        }
    }

    private static DomainException unavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_OCCUPANCY_UNAVAILABLE",
                "Authoritative Booking occupancy is unavailable; no availability is being claimed");
    }
}
