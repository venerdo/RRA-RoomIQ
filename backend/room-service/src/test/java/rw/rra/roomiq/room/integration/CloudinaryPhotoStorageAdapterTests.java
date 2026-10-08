package rw.rra.roomiq.room.integration;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import rw.rra.roomiq.common.web.DomainException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryPhotoStorageAdapterTests {
    @Test
    void refusesUploadAndDeleteWhenProviderCredentialsAreNotConfigured() {
        CloudinaryPhotoStorageAdapter adapter =
                new CloudinaryPhotoStorageAdapter("", "", "", "rra-roomiq/rooms");

        assertThatThrownBy(() -> adapter.upload(UUID.randomUUID(), new byte[]{1}, "photo.png", "image/png"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not configured");
        assertThatThrownBy(() -> adapter.delete("roomiq/rooms/photo"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void uploadsToRoomScopedFolderAndDeletesProviderAsset() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("public_id", "roomiq/rooms/photo", "secure_url",
                        "https://res.cloudinary.com/test/room-photo.png"));
        when(uploader.destroy(eq("roomiq/rooms/photo"), anyMap())).thenReturn(Map.of("result", "ok"));
        CloudinaryPhotoStorageAdapter adapter = new CloudinaryPhotoStorageAdapter(cloudinary, "rra-roomiq/rooms");
        UUID roomId = UUID.randomUUID();

        CloudinaryPhotoStorage.StoredPhoto stored = adapter.upload(roomId,
                new byte[]{(byte) 0x89, 0x50}, "meeting room.png", "image/png");

        assertThat(stored.publicId()).isEqualTo("roomiq/rooms/photo");
        assertThat(stored.secureUrl()).startsWith("https://");
        verify(uploader).upload(any(byte[].class), org.mockito.ArgumentMatchers.argThat(options ->
                "image".equals(options.get("resource_type"))
                        && ("rra-roomiq/rooms/" + roomId).equals(options.get("folder"))));

        adapter.delete(stored.publicId());
        verify(uploader).destroy(eq(stored.publicId()), anyMap());
    }

        @Test
        void mapsMalformedProviderResponsesAndProviderFailuresToSafeErrors() throws Exception {
                Cloudinary cloudinary = mock(Cloudinary.class);
                Uploader uploader = mock(Uploader.class);
                when(cloudinary.uploader()).thenReturn(uploader);
                CloudinaryPhotoStorageAdapter adapter = new CloudinaryPhotoStorageAdapter(cloudinary, "rra-roomiq/rooms");
                UUID roomId = UUID.randomUUID();

                when(uploader.upload(any(byte[].class), anyMap()))
                                .thenReturn(Map.of("public_id", "roomiq/rooms/photo", "secure_url", "http://example.test/photo.png"));
                assertThatThrownBy(() -> adapter.upload(roomId, new byte[]{1}, "photo.png", "image/png"))
                                .isInstanceOfSatisfying(DomainException.class, exception -> {
                                        assertThat(exception.code()).isEqualTo("CLOUDINARY_UNAVAILABLE");
                                        assertThat(exception.status().value()).isEqualTo(503);
                                });

                when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("provider response detail"));
                assertThatThrownBy(() -> adapter.upload(roomId, new byte[]{1}, "photo.png", "image/png"))
                                .isInstanceOfSatisfying(DomainException.class, exception -> {
                                        assertThat(exception.code()).isEqualTo("CLOUDINARY_UNAVAILABLE");
                                        assertThat(exception.getMessage()).doesNotContain("provider response detail");
                                });

                when(uploader.destroy(eq("roomiq/rooms/photo"), anyMap())).thenReturn(Map.of("result", "error"));
                assertThatThrownBy(() -> adapter.delete("roomiq/rooms/photo"))
                                .isInstanceOfSatisfying(DomainException.class, exception ->
                                                assertThat(exception.code()).isEqualTo("CLOUDINARY_UNAVAILABLE"));

                when(uploader.destroy(eq("roomiq/rooms/photo"), anyMap())).thenThrow(new IOException("provider delete detail"));
                assertThatThrownBy(() -> adapter.delete("roomiq/rooms/photo"))
                                .isInstanceOfSatisfying(DomainException.class, exception -> {
                                        assertThat(exception.code()).isEqualTo("CLOUDINARY_UNAVAILABLE");
                                        assertThat(exception.getMessage()).doesNotContain("provider delete detail");
                                });
        }
}