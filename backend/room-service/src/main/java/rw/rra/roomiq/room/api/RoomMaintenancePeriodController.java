package rw.rra.roomiq.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.room.domain.dto.CreateMaintenancePeriodRequest;
import rw.rra.roomiq.room.domain.dto.MaintenancePeriodDto;
import rw.rra.roomiq.room.domain.service.RoomLifecycleManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/maintenance-periods")
@Tag(name = "Room maintenance")
public class RoomMaintenancePeriodController {
    private final RoomLifecycleManagementService lifecycleService;

    public RoomMaintenancePeriodController(RoomLifecycleManagementService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    @Operation(summary = "List room maintenance periods")
    public ApiResponse<List<MaintenancePeriodDto>> list(@PathVariable UUID roomId) {
        return ApiResponse.success("Maintenance periods retrieved", lifecycleService.listMaintenancePeriods(roomId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a room maintenance period")
    public ApiResponse<MaintenancePeriodDto> create(@PathVariable UUID roomId,
                                                     @Valid @RequestBody CreateMaintenancePeriodRequest request) {
        return ApiResponse.success("Maintenance period created",
                lifecycleService.createMaintenancePeriod(roomId, request));
    }

    @DeleteMapping("/{maintenancePeriodId}")
    @Operation(summary = "Delete a room maintenance period")
    public ApiResponse<Void> delete(@PathVariable UUID roomId, @PathVariable UUID maintenancePeriodId) {
        lifecycleService.deleteMaintenancePeriod(roomId, maintenancePeriodId);
        return ApiResponse.success("Maintenance period deleted", null);
    }
}