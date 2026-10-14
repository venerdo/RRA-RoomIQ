package rw.rra.roomiq.booking.domain.dto;

import java.util.List;
import java.util.UUID;

public record RoomEventRecipientsResponse(String contractVersion, UUID roomId, List<UUID> recipientUserIds) {
}
