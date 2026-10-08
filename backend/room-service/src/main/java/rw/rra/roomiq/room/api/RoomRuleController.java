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
import rw.rra.roomiq.room.domain.dto.RoomRuleDto;
import rw.rra.roomiq.room.domain.dto.SetRoomRuleRequest;
import rw.rra.roomiq.room.domain.service.RoomRuleManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Room rules")
public class RoomRuleController {
    private final RoomRuleManagementService service;

    public RoomRuleController(RoomRuleManagementService service) {
        this.service = service;
    }

    @GetMapping("/rooms/{roomId}/rules")
    @Operation(summary = "List rules for a room")
    public ApiResponse<List<RoomRuleDto>> listForRoom(@PathVariable UUID roomId) {
        return ApiResponse.success("Room rules retrieved", service.listForRoom(roomId));
    }

    @PostMapping("/rooms/{roomId}/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a room-specific rule")
    public ApiResponse<RoomRuleDto> createForRoom(@PathVariable UUID roomId,
                                                  @Valid @RequestBody SetRoomRuleRequest request) {
        return ApiResponse.success("Room rule created", service.createForRoom(roomId, request));
    }

    @GetMapping("/office-buildings/{officeBuildingId}/room-rules")
    @Operation(summary = "List building-default room rules")
    public ApiResponse<List<RoomRuleDto>> listBuildingDefaults(@PathVariable UUID officeBuildingId) {
        return ApiResponse.success("Building room rules retrieved", service.listBuildingDefaults(officeBuildingId));
    }

    @PostMapping("/office-buildings/{officeBuildingId}/room-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a building-default room rule")
    public ApiResponse<RoomRuleDto> createBuildingDefault(@PathVariable UUID officeBuildingId,
                                                          @Valid @RequestBody SetRoomRuleRequest request) {
        return ApiResponse.success("Building room rule created",
                service.createBuildingDefault(officeBuildingId, request));
    }

    @GetMapping("/room-rules/{ruleId}")
    @Operation(summary = "Get a room rule")
    public ApiResponse<RoomRuleDto> get(@PathVariable UUID ruleId) {
        return ApiResponse.success("Room rule retrieved", service.get(ruleId));
    }

    @PutMapping("/room-rules/{ruleId}")
    @Operation(summary = "Update a room rule")
    public ApiResponse<RoomRuleDto> update(@PathVariable UUID ruleId,
                                           @Valid @RequestBody SetRoomRuleRequest request) {
        return ApiResponse.success("Room rule updated", service.update(ruleId, request));
    }
}