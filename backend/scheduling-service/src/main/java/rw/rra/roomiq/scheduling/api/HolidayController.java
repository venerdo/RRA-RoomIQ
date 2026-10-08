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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.scheduling.config.SchedulingAuthorizationInterceptor;
import rw.rra.roomiq.scheduling.domain.dto.HolidayListQuery;
import rw.rra.roomiq.scheduling.domain.dto.HolidayPageResponse;
import rw.rra.roomiq.scheduling.domain.dto.HolidayResponse;
import rw.rra.roomiq.scheduling.domain.dto.SetHolidayRequest;
import rw.rra.roomiq.scheduling.domain.service.HolidayManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/holidays")
@Tag(name = "Holidays")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation or scope error"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scheduling access denied"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Holiday or calendar not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Holiday scope/date conflict"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Identity authorization unavailable")
})
public class HolidayController {
    private final HolidayManagementService service;

    public HolidayController(HolidayManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List holidays by date and scope")
    public ApiResponse<HolidayPageResponse> list(@Valid @ModelAttribute HolidayListQuery query) {
        return ApiResponse.success("Holidays retrieved", service.list(query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a calendar, building, or nationwide holiday")
    public ApiResponse<HolidayResponse> create(@Valid @RequestBody SetHolidayRequest request,
            @RequestAttribute(SchedulingAuthorizationInterceptor.ACTOR_USER_ID_ATTRIBUTE) UUID actorUserId) {
        return ApiResponse.success("Holiday created", service.create(request, actorUserId));
    }

    @GetMapping("/{holidayId}")
    @Operation(summary = "Get a holiday")
    public ApiResponse<HolidayResponse> get(@PathVariable UUID holidayId) {
        return ApiResponse.success("Holiday retrieved", service.get(holidayId));
    }

    @PutMapping("/{holidayId}")
    @Operation(summary = "Update a holiday")
    public ApiResponse<HolidayResponse> update(@PathVariable UUID holidayId,
                                               @Valid @RequestBody SetHolidayRequest request) {
        return ApiResponse.success("Holiday updated", service.update(holidayId, request));
    }

    @DeleteMapping("/{holidayId}")
    @Operation(summary = "Delete a holiday")
    public ApiResponse<Void> delete(@PathVariable UUID holidayId) {
        service.delete(holidayId);
        return ApiResponse.success("Holiday deleted", null);
    }
}