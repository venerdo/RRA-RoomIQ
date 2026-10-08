package rw.rra.roomiq.room.integration;

import java.util.UUID;

public interface RoomPhotoAuthorizationClient {
    UUID authorizeRoomManagement(UUID officeBuildingId);
}