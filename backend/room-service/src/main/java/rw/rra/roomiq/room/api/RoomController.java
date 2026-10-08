package rw.rra.roomiq.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.room.domain.dto.CatalogPageResponse;
import rw.rra.roomiq.room.domain.dto.CreateRoomRequest;
import rw.rra.roomiq.room.domain.dto.ChangeRoomStatusRequest;
import rw.rra.roomiq.room.domain.dto.RoomDto;
import rw.rra.roomiq.room.domain.dto.RoomPageQuery;
import rw.rra.roomiq.room.domain.dto.RoomStatusHistoryDto;
import rw.rra.roomiq.room.domain.service.RoomLifecycleManagementService;
import rw.rra.roomiq.room.domain.service.RoomManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Rooms")
public class RoomController {
    private final RoomManagementService service;
    private final RoomLifecycleManagementService lifecycleService;

    public RoomController(RoomManagementService service, RoomLifecycleManagementService lifecycleService) {
        this.service = service;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/rooms")
    @Operation(summary = "List rooms")
    public ApiResponse<CatalogPageResponse<RoomDto>> list(@Valid @ModelAttribute RoomPageQuery query) {
        return ApiResponse.success("Rooms retrieved", service.listRooms(query));
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a room")
    public ApiResponse<RoomDto> create(@Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.success("Room created", service.createRoom(request));
    }

    @GetMapping("/rooms/{id}")
    @Operation(summary = "Get a room")
    public ApiResponse<RoomDto> get(@PathVariable UUID id) {
        return ApiResponse.success("Room retrieved", service.getRoom(id));
    }

    @PutMapping("/rooms/{id}")
    @Operation(summary = "Update a room")
    public ApiResponse<RoomDto> update(@PathVariable UUID id,
                                     @Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.success("Room updated", service.updateRoom(id, request));
    }

    @PatchMapping("/rooms/{id}/status")
    @Operation(summary = "Change room status")
    public ApiResponse<RoomStatusHistoryDto> changeStatus(@PathVariable UUID id,
                                                           @Valid @RequestBody ChangeRoomStatusRequest request) {
        return ApiResponse.success("Room status updated", lifecycleService.changeStatus(id, request));
    }

    @GetMapping("/rooms/{id}/status-history")
    @Operation(summary = "List room status history")
    public ApiResponse<List<RoomStatusHistoryDto>> statusHistory(@PathVariable UUID id) {
        return ApiResponse.success("Room status history retrieved", lifecycleService.listStatusHistory(id));
    }

    @DeleteMapping("/rooms/{id}")
    @Operation(summary = "Soft-delete a room")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        lifecycleService.softDelete(id);
        return ApiResponse.success("Room deleted", null);
    }
}
