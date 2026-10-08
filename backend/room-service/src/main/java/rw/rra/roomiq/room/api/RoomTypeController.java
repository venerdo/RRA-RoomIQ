package rw.rra.roomiq.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.room.domain.dto.CatalogPageResponse;
import rw.rra.roomiq.room.domain.dto.CreateRoomTypeRequest;
import rw.rra.roomiq.room.domain.dto.RoomTypeDto;
import rw.rra.roomiq.room.domain.dto.RoomTypePageQuery;
import rw.rra.roomiq.room.domain.service.RoomManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/room-types")
@Tag(name = "Room types")
public class RoomTypeController {
    private final RoomManagementService service;

    public RoomTypeController(RoomManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List room types")
    public ApiResponse<CatalogPageResponse<RoomTypeDto>> list(@Valid @ModelAttribute RoomTypePageQuery query) {
        return ApiResponse.success("Room types retrieved", service.listRoomTypes(query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a room type")
    public ApiResponse<RoomTypeDto> create(@Valid @RequestBody CreateRoomTypeRequest request) {
        return ApiResponse.success("Room type created", service.createRoomType(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a room type")
    public ApiResponse<RoomTypeDto> get(@PathVariable UUID id) {
        return ApiResponse.success("Room type retrieved", service.getRoomType(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a room type")
    public ApiResponse<RoomTypeDto> update(@PathVariable UUID id,
                                          @Valid @RequestBody CreateRoomTypeRequest request) {
        return ApiResponse.success("Room type updated", service.updateRoomType(id, request));
    }
}
