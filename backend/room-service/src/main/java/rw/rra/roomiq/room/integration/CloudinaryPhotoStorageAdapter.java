package rw.rra.roomiq.room.integration;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import rw.rra.roomiq.common.web.DomainException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class CloudinaryPhotoStorageAdapter implements CloudinaryPhotoStorage {
    private final Cloudinary cloudinary;
    private final String folder;

    @Autowired
    public CloudinaryPhotoStorageAdapter(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.folder:rra-roomiq/rooms}") String folder) {
        this(configuredCloudinary(cloudName, apiKey, apiSecret), folder);
    }

    CloudinaryPhotoStorageAdapter(Cloudinary cloudinary, String folder) {
        this.cloudinary = cloudinary;
        this.folder = folder;
    }

    @Override
    public StoredPhoto upload(UUID roomId, byte[] imageBytes, String originalFilename, String contentType) {
        requireConfigured();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(imageBytes, ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", folder + "/" + roomId,
                    "use_filename", false,
                    "unique_filename", true,
                    "overwrite", false,
                    "context", ObjectUtils.asMap("original_filename", safeFilename(originalFilename),
                            "content_type", contentType)));
            Object publicId = result.get("public_id");
            Object secureUrl = result.get("secure_url");
            if (!(publicId instanceof String id) || id.isBlank()
                    || !(secureUrl instanceof String url) || !url.startsWith("https://")) {
                throw unavailable();
            }
            return new StoredPhoto(id, url);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String publicId) {
        requireConfigured();
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap("invalidate", true, "resource_type", "image"));
            Object outcome = result.get("result");
            if (!"ok".equals(outcome) && !"not found".equals(outcome)) {
                throw unavailable();
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private void requireConfigured() {
        if (cloudinary == null) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "CLOUDINARY_NOT_CONFIGURED",
                    "Photo storage is not configured");
        }
    }

    private static Cloudinary configuredCloudinary(String cloudName, String apiKey, String apiSecret) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return null;
        }
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("cloud_name", cloudName);
        configuration.put("api_key", apiKey);
        configuration.put("api_secret", apiSecret);
        configuration.put("secure", true);
        return new Cloudinary(configuration);
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "room-photo";
        }
        String basename = filename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        return basename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static DomainException unavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "CLOUDINARY_UNAVAILABLE",
                "Photo storage is unavailable");
    }
}