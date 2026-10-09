package rw.rra.roomiq.scheduling.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingOccupancyClient {
    OccupancySnapshot occupancy(List<UUID> roomIds, Instant startsAt, Instant endsAt);

    record OccupancySnapshot(Instant snapshotAt, List<RoomOccupancy> rooms) {
    }

    record RoomOccupancy(UUID roomId, List<OccupiedInterval> intervals) {
    }

    record OccupiedInterval(Instant startsAt, Instant endsAt) {
    }
}
