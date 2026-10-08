package rw.rra.roomiq.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.room.domain.dto.RoomFacilityDto;
import rw.rra.roomiq.room.domain.dto.SetRoomFacilityRequest;
import rw.rra.roomiq.room.domain.service.RoomFacilityManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/facilities")
@Tag(name = "Room facilities")
public class RoomFacilityController {
    private final RoomFacilityManagementService service;

    public RoomFacilityController(RoomFacilityManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List facilities assigned to a room")
    public ApiResponse<List<RoomFacilityDto>> list(@PathVariable UUID roomId) {
        return ApiResponse.success("Room facilities retrieved", service.list(roomId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign a facility type to a room")
    public ApiResponse<RoomFacilityDto> create(@PathVariable UUID roomId,
                                               @Valid @RequestBody SetRoomFacilityRequest request) {
        return ApiResponse.success("Room facility assigned", service.create(roomId, request));
    }

    @PutMapping("/{assignmentId}")
    @Operation(summary = "Update a room facility assignment")
    public ApiResponse<RoomFacilityDto> update(@PathVariable UUID roomId,
                                               @PathVariable UUID assignmentId,
                                               @Valid @RequestBody SetRoomFacilityRequest request) {
        return ApiResponse.success("Room facility updated", service.update(roomId, assignmentId, request));
    }
}