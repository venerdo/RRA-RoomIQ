package rw.rra.roomiq.room.domain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.room.domain.dto.RoomPhotoDto;
import rw.rra.roomiq.room.domain.dto.UpdateRoomPhotoRequest;
import rw.rra.roomiq.room.domain.entity.Room;
import rw.rra.roomiq.room.domain.entity.RoomPhoto;
import rw.rra.roomiq.room.domain.mapper.RoomMapper;
import rw.rra.roomiq.room.domain.repository.RoomPhotoRepository;
import rw.rra.roomiq.room.domain.repository.RoomRepository;
import rw.rra.roomiq.room.integration.CloudinaryPhotoStorage;
import rw.rra.roomiq.room.integration.RoomPhotoAuthorizationClient;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RoomPhotoManagementService {
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final long DEFAULT_MAX_PHOTOS_PER_ROOM = 10;
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", "JPEG",
            "image/png", "PNG",
            "image/webp", "WEBP");

    private final RoomRepository roomRepository;
    private final RoomPhotoRepository roomPhotoRepository;
    private final CloudinaryPhotoStorage cloudinaryPhotoStorage;
    private final RoomPhotoAuthorizationClient authorizationClient;
    private final RoomMapper roomMapper;
    private final long maxFileSizeBytes;
    private final long maxPhotosPerRoom;

    public RoomPhotoManagementService(RoomRepository roomRepository,
                                      RoomPhotoRepository roomPhotoRepository,
                                      CloudinaryPhotoStorage cloudinaryPhotoStorage,
                                      RoomPhotoAuthorizationClient authorizationClient,
                                      RoomMapper roomMapper,
                                      @Value("${room.photos.max-file-size-bytes:5242880}") long maxFileSizeBytes,
                                      @Value("${room.photos.max-count:10}") long maxPhotosPerRoom) {
        this.roomRepository = roomRepository;
        this.roomPhotoRepository = roomPhotoRepository;
        this.cloudinaryPhotoStorage = cloudinaryPhotoStorage;
        this.authorizationClient = authorizationClient;
        this.roomMapper = roomMapper;
        this.maxFileSizeBytes = maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
        this.maxPhotosPerRoom = maxPhotosPerRoom > 0 ? maxPhotosPerRoom : DEFAULT_MAX_PHOTOS_PER_ROOM;
    }

    public List<RoomPhotoDto> list(UUID roomId) {
        Room room = requireRoom(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        return roomPhotoRepository.findAllByRoom_IdOrderBySortOrderAscCreatedAtAsc(roomId)
                .stream().map(roomMapper::toDto).toList();
    }

    @Transactional
    public RoomPhotoDto upload(UUID roomId, MultipartFile file, Integer sortOrder,
                               boolean primary, boolean approvedForPublic) {
        Room room = requireRoomForUpdate(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        validateSortOrder(sortOrder);
        byte[] bytes = readAndValidate(file);
        if (roomPhotoRepository.countByRoom_Id(roomId) >= maxPhotosPerRoom) {
            throw invalid("ROOM_PHOTO_LIMIT_EXCEEDED", "The room has reached its photo limit");
        }

        String contentType = normalizeContentType(file.getContentType());
        CloudinaryPhotoStorage.StoredPhoto stored = cloudinaryPhotoStorage.upload(
                roomId, bytes, file.getOriginalFilename(), contentType);
        try {
            validateStoredPhoto(stored);
            RoomPhoto photo = new RoomPhoto(room, stored.publicId(), stored.secureUrl(),
                    sortOrder == null ? null : sortOrder.shortValue(), primary, approvedForPublic);
            return roomMapper.toDto(roomPhotoRepository.saveAndFlush(photo));
        } catch (RuntimeException exception) {
            if (stored != null && stored.publicId() != null && !stored.publicId().isBlank()) {
                try {
                    cloudinaryPhotoStorage.delete(stored.publicId());
                } catch (RuntimeException ignored) {
                    // Keep the original failure as the observable error.
                }
            }
            throw exception;
        }
    }

    @Transactional
    public RoomPhotoDto update(UUID roomId, UUID photoId, UpdateRoomPhotoRequest request) {
        Room room = requireRoomForUpdate(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        validateSortOrder(request.sortOrder());
        RoomPhoto photo = roomPhotoRepository.findByIdAndRoom_Id(photoId, roomId)
                .orElseThrow(() -> notFound("ROOM_PHOTO_NOT_FOUND", "Room photo"));
        photo.updateMetadata(request.sortOrder() == null ? null : request.sortOrder().shortValue(),
                request.primary(), request.approvedForPublic());
        return roomMapper.toDto(roomPhotoRepository.saveAndFlush(photo));
    }

    @Transactional
    public void delete(UUID roomId, UUID photoId) {
        Room room = requireRoomForUpdate(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        RoomPhoto photo = roomPhotoRepository.findByIdAndRoom_Id(photoId, roomId)
                .orElseThrow(() -> notFound("ROOM_PHOTO_NOT_FOUND", "Room photo"));
        cloudinaryPhotoStorage.delete(photo.getCloudinaryPublicId());
        roomPhotoRepository.delete(photo);
        roomPhotoRepository.flush();
    }

    private Room requireRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room"));
        if (room.getDeletedAt() != null) {
            throw notFound("ROOM_NOT_FOUND", "Room");
        }
        return room;
    }

    private Room requireRoomForUpdate(UUID roomId) {
        Room room = roomRepository.findByIdForPhotoManagement(roomId)
                .orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room"));
        if (room.getDeletedAt() != null) {
            throw notFound("ROOM_NOT_FOUND", "Room");
        }
        return room;
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("ROOM_PHOTO_REQUIRED", "A photo file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw invalid("ROOM_PHOTO_TOO_LARGE", "Photo exceeds the maximum file size");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_IMAGE_TYPES.containsKey(contentType)) {
            throw invalid("ROOM_PHOTO_TYPE_UNSUPPORTED", "Only JPEG, PNG, and WEBP images are accepted");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!matchesSignature(bytes, ALLOWED_IMAGE_TYPES.get(contentType))) {
                throw invalid("ROOM_PHOTO_TYPE_UNSUPPORTED", "Photo content does not match its image type");
            }
            return bytes;
        } catch (IOException exception) {
            throw invalid("ROOM_PHOTO_READ_FAILED", "Photo could not be read");
        }
    }

    private static boolean matchesSignature(byte[] bytes, String imageType) {
        return switch (imageType) {
            case "JPEG" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "PNG" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                    && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "WEBP" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                    && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static void validateSortOrder(Integer sortOrder) {
        if (sortOrder != null && (sortOrder < 0 || sortOrder > Short.MAX_VALUE)) {
            throw invalid("ROOM_PHOTO_SORT_ORDER_INVALID", "Photo sort order is outside the supported range");
        }
    }

    private static void validateStoredPhoto(CloudinaryPhotoStorage.StoredPhoto stored) {
        if (stored == null || stored.publicId() == null || stored.publicId().isBlank()
                || stored.publicId().length() > 255 || stored.secureUrl() == null
                || !stored.secureUrl().startsWith("https://")) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "CLOUDINARY_RESPONSE_INVALID",
                    "Photo provider returned invalid asset metadata");
        }
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static DomainException notFound(String code, String label) {
        return new DomainException(HttpStatus.NOT_FOUND, code, label + " not found");
    }
}