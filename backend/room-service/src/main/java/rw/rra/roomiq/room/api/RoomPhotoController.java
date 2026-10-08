package rw.rra.roomiq.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.room.domain.dto.RoomPhotoDto;
import rw.rra.roomiq.room.domain.dto.UpdateRoomPhotoRequest;
import rw.rra.roomiq.room.domain.service.RoomPhotoManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/photos")
@Tag(name = "Room photos")
public class RoomPhotoController {
    private final RoomPhotoManagementService service;

    public RoomPhotoController(RoomPhotoManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List room photos")
    public ApiResponse<List<RoomPhotoDto>> list(@PathVariable UUID roomId) {
        return ApiResponse.success("Room photos retrieved", service.list(roomId));
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a room photo")
    public ApiResponse<RoomPhotoDto> upload(@PathVariable UUID roomId,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(required = false) Integer sortOrder,
                                            @RequestParam(defaultValue = "false") boolean primary,
                                            @RequestParam(defaultValue = "false") boolean approvedForPublic) {
        return ApiResponse.success("Room photo uploaded",
                service.upload(roomId, file, sortOrder, primary, approvedForPublic));
    }

    @PutMapping("/{photoId}")
    @Operation(summary = "Update room photo metadata")
    public ApiResponse<RoomPhotoDto> update(@PathVariable UUID roomId,
                                            @PathVariable UUID photoId,
                                            @Valid @RequestBody UpdateRoomPhotoRequest request) {
        return ApiResponse.success("Room photo updated", service.update(roomId, photoId, request));
    }

    @DeleteMapping("/{photoId}")
    @Operation(summary = "Delete a room photo")
    public ApiResponse<Void> delete(@PathVariable UUID roomId, @PathVariable UUID photoId) {
        service.delete(roomId, photoId);
        return ApiResponse.success("Room photo deleted", null);
    }
}