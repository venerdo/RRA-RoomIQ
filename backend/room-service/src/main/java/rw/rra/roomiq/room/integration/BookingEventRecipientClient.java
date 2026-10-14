package rw.rra.roomiq.room.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingEventRecipientClient {
    List<UUID> resolveRoomEventRecipients(UUID roomId, UUID officeBuildingId, Instant startsAt, Instant endsAt);
}
