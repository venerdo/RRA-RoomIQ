package rw.rra.roomiq.booking.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingOccupancySnapshotResponse(Instant snapshotAt, List<RoomOccupancy> rooms) {
    public record RoomOccupancy(UUID roomId, List<OccupiedInterval> intervals) {
    }

    public record OccupiedInterval(Instant startsAt, Instant endsAt) {
    }
}
