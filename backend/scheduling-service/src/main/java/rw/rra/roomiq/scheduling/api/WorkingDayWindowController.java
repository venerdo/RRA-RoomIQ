package rw.rra.roomiq.scheduling.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.scheduling.domain.dto.SetWorkingDayWindowRequest;
import rw.rra.roomiq.scheduling.domain.dto.WorkingDayWindowResponse;
import rw.rra.roomiq.scheduling.domain.service.WorkingCalendarManagementService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/working-calendars/{calendarId}/working-day-windows")
@Tag(name = "Working-day windows")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scheduling access denied"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Calendar or window not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Identity authorization unavailable")
})
public class WorkingDayWindowController {
    private final WorkingCalendarManagementService service;

    public WorkingDayWindowController(WorkingCalendarManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List working-day windows for a calendar")
    public ApiResponse<List<WorkingDayWindowResponse>> list(@PathVariable UUID calendarId) {
        return ApiResponse.success("Working-day windows retrieved", service.listWindows(calendarId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a working-day window")
    public ApiResponse<WorkingDayWindowResponse> create(@PathVariable UUID calendarId,
                                                        @Valid @RequestBody SetWorkingDayWindowRequest request) {
        return ApiResponse.success("Working-day window created", service.createWindow(calendarId, request));
    }

    @PutMapping("/{windowId}")
    @Operation(summary = "Update a working-day window")
    public ApiResponse<WorkingDayWindowResponse> update(@PathVariable UUID calendarId,
                                                        @PathVariable UUID windowId,
                                                        @Valid @RequestBody SetWorkingDayWindowRequest request) {
        return ApiResponse.success("Working-day window updated",
                service.updateWindow(calendarId, windowId, request));
    }

    @DeleteMapping("/{windowId}")
    @Operation(summary = "Delete a working-day window")
    public ApiResponse<Void> delete(@PathVariable UUID calendarId, @PathVariable UUID windowId) {
        service.deleteWindow(calendarId, windowId);
        return ApiResponse.success("Working-day window deleted", null);
    }
}