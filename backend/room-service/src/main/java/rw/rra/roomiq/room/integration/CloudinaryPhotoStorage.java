package rw.rra.roomiq.room.integration;

import java.util.UUID;

public interface CloudinaryPhotoStorage {
    StoredPhoto upload(UUID roomId, byte[] imageBytes, String originalFilename, String contentType);

    void delete(String publicId);

    record StoredPhoto(String publicId, String secureUrl) { }
}