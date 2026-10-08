package rw.rra.roomiq.scheduling.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.scheduling.domain.dto.SetWorkingCalendarRequest;
import rw.rra.roomiq.scheduling.domain.dto.WorkingCalendarListQuery;
import rw.rra.roomiq.scheduling.domain.dto.WorkingCalendarPageResponse;
import rw.rra.roomiq.scheduling.domain.dto.WorkingCalendarResponse;
import rw.rra.roomiq.scheduling.domain.service.WorkingCalendarManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/working-calendars")
@Tag(name = "Working calendars")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scheduling access denied"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Calendar not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Calendar conflicts with existing data"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Identity authorization unavailable")
})
public class WorkingCalendarController {
    private final WorkingCalendarManagementService service;

    public WorkingCalendarController(WorkingCalendarManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List working calendars")
    public ApiResponse<WorkingCalendarPageResponse> list(@Valid @ModelAttribute WorkingCalendarListQuery query) {
        return ApiResponse.success("Working calendars retrieved", service.list(query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a working calendar")
    public ApiResponse<WorkingCalendarResponse> create(@Valid @RequestBody SetWorkingCalendarRequest request) {
        return ApiResponse.success("Working calendar created", service.create(request));
    }

    @GetMapping("/{calendarId}")
    @Operation(summary = "Get a working calendar")
    public ApiResponse<WorkingCalendarResponse> get(@PathVariable UUID calendarId) {
        return ApiResponse.success("Working calendar retrieved", service.get(calendarId));
    }

    @PutMapping("/{calendarId}")
    @Operation(summary = "Update a working calendar")
    public ApiResponse<WorkingCalendarResponse> update(@PathVariable UUID calendarId,
                                                       @Valid @RequestBody SetWorkingCalendarRequest request) {
        return ApiResponse.success("Working calendar updated", service.update(calendarId, request));
    }

    @DeleteMapping("/{calendarId}")
    @Operation(summary = "Delete a working calendar without working-day windows")
    public ApiResponse<Void> delete(@PathVariable UUID calendarId) {
        service.delete(calendarId);
        return ApiResponse.success("Working calendar deleted", null);
    }
}