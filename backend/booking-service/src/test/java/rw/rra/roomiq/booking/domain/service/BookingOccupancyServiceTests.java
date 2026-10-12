package rw.rra.roomiq.booking.domain.service;

import org.junit.jupiter.api.Test;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancyRequest;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancySnapshotResponse;
import rw.rra.roomiq.booking.domain.repository.BookingOccupancyRepository;
import rw.rra.roomiq.booking.domain.repository.BookingOccupancyRepository.OccupancyInterval;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookingOccupancyServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-12T06:00:00Z");
    private static final Instant START = Instant.parse("2026-10-12T10:00:00Z");
    private static final Instant END = Instant.parse("2026-10-12T11:00:00Z");

    private final BookingOccupancyRepository repository = mock(BookingOccupancyRepository.class);
    private final BookingOccupancyService service =
            new BookingOccupancyService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void returnsCompleteRoomSetAndValidFiniteHalfOpenIntervals() {
        UUID occupiedRoomId = UUID.randomUUID();
        UUID availableRoomId = UUID.randomUUID();
        when(repository.findActiveIntervals(List.of(occupiedRoomId, availableRoomId), START, END))
                .thenReturn(List.of(new OccupancyInterval(occupiedRoomId, START, END, false, true, false)));

        var response = service.snapshot(new BookingOccupancyRequest(
                List.of(occupiedRoomId, availableRoomId), START, END, "v1"));

        assertThat(response.snapshotAt()).isEqualTo(NOW);
        assertThat(response.rooms()).hasSize(2);
        assertThat(response.rooms().get(0).roomId()).isEqualTo(occupiedRoomId);
        assertThat(response.rooms().get(0).intervals()).containsExactly(
                new BookingOccupancySnapshotResponse.OccupiedInterval(START, END));
        assertThat(response.rooms().get(1).roomId()).isEqualTo(availableRoomId);
        assertThat(response.rooms().get(1).intervals()).isEmpty();
        verify(repository).findActiveIntervals(List.of(occupiedRoomId, availableRoomId), START, END);
    }

    @Test
    void rejectsUnsupportedVersionInvalidRangesAndDuplicateRoomsBeforeQuery() {
        UUID roomId = UUID.randomUUID();

        assertInvalid(new BookingOccupancyRequest(List.of(roomId), START, END, "v2"));
        assertInvalid(new BookingOccupancyRequest(List.of(roomId), END, START, "v1"));
        assertInvalid(new BookingOccupancyRequest(List.of(roomId, roomId), START, END, "v1"));
        assertInvalid(new BookingOccupancyRequest(List.of(), START, END, "v1"));

        verifyNoInteractions(repository);
    }

    @Test
    void failsClosedWhenRepositoryReturnsMalformedOrUnrequestedOccupancy() {
        UUID requestedRoomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        BookingOccupancyRequest request = new BookingOccupancyRequest(
                List.of(requestedRoomId), START, END, "v1");
        when(repository.findActiveIntervals(List.of(requestedRoomId), START, END)).thenReturn(List.of(
                new OccupancyInterval(requestedRoomId, END, START, false, true, false)));

        assertUnavailable(request);

        when(repository.findActiveIntervals(List.of(requestedRoomId), START, END)).thenReturn(List.of(
                new OccupancyInterval(otherRoomId, START, END, false, true, false)));

        assertUnavailable(request);
    }

    @Test
    void failsClosedForEmptyUnboundedOrNonHalfOpenIntervals() {
        UUID roomId = UUID.randomUUID();
        BookingOccupancyRequest request = new BookingOccupancyRequest(List.of(roomId), START, END, "v1");
        for (OccupancyInterval malformed : List.of(
                new OccupancyInterval(roomId, START, END, true, true, false),
                new OccupancyInterval(roomId, null, END, false, true, false),
                new OccupancyInterval(roomId, START, null, false, true, false),
                new OccupancyInterval(roomId, START, END, false, false, false),
                new OccupancyInterval(roomId, START, END, false, true, true))) {
            when(repository.findActiveIntervals(List.of(roomId), START, END)).thenReturn(List.of(malformed));
            assertUnavailable(request);
        }
    }

    private void assertInvalid(BookingOccupancyRequest request) {
        assertThatThrownBy(() -> service.snapshot(request))
                .isInstanceOf(DomainException.class)
                .hasMessage("The versioned occupancy request is invalid");
    }

    private void assertUnavailable(BookingOccupancyRequest request) {
        assertThatThrownBy(() -> service.snapshot(request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Authoritative Booking occupancy is unavailable");
    }
}
