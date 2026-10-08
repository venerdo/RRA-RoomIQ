package rw.rra.roomiq.scheduling.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import rw.rra.roomiq.scheduling.config.SchedulingAuthorizationInterceptor;
import rw.rra.roomiq.scheduling.domain.dto.RecurrenceOccurrencesResponse;
import rw.rra.roomiq.scheduling.domain.dto.RecurrenceRuleListQuery;
import rw.rra.roomiq.scheduling.domain.dto.RecurrenceRulePageResponse;
import rw.rra.roomiq.scheduling.domain.dto.RecurrenceRuleResponse;
import rw.rra.roomiq.scheduling.domain.dto.SetRecurrenceRuleRequest;
import rw.rra.roomiq.scheduling.domain.service.RecurrenceRuleManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recurrence-rules")
@Tag(name = "Recurrence rules")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "RRULE, timezone, or request validation failed"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scheduling access denied"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recurrence rule not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Identity authorization unavailable")
})
public class RecurrenceRuleController {
    private final RecurrenceRuleManagementService service;

    public RecurrenceRuleController(RecurrenceRuleManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List recurrence rules")
    public ApiResponse<RecurrenceRulePageResponse> list(@Valid @ModelAttribute RecurrenceRuleListQuery query) {
        return ApiResponse.success("Recurrence rules retrieved", service.list(query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create and validate an RRULE recurrence rule")
    public ApiResponse<RecurrenceRuleResponse> create(@Valid @RequestBody SetRecurrenceRuleRequest request,
                                                       HttpServletRequest httpRequest) {
        return ApiResponse.success("Recurrence rule created",
                service.create(request, actorUserId(httpRequest)));
    }

    @GetMapping("/{recurrenceRuleId}")
    @Operation(summary = "Get a recurrence rule")
    public ApiResponse<RecurrenceRuleResponse> get(@PathVariable UUID recurrenceRuleId) {
        return ApiResponse.success("Recurrence rule retrieved", service.get(recurrenceRuleId));
    }

    @PutMapping("/{recurrenceRuleId}")
    @Operation(summary = "Update and validate an RRULE recurrence rule")
    public ApiResponse<RecurrenceRuleResponse> update(@PathVariable UUID recurrenceRuleId,
                                                       @Valid @RequestBody SetRecurrenceRuleRequest request) {
        return ApiResponse.success("Recurrence rule updated", service.update(recurrenceRuleId, request));
    }

    @GetMapping("/{recurrenceRuleId}/occurrences")
    @Operation(summary = "Evaluate a bounded recurrence rule into local dates")
    public ApiResponse<RecurrenceOccurrencesResponse> occurrences(@PathVariable UUID recurrenceRuleId) {
        return ApiResponse.success("Recurrence occurrences evaluated", service.occurrences(recurrenceRuleId));
    }

    @DeleteMapping("/{recurrenceRuleId}")
    @Operation(summary = "Delete a recurrence rule")
    public ApiResponse<Void> delete(@PathVariable UUID recurrenceRuleId) {
        service.delete(recurrenceRuleId);
        return ApiResponse.success("Recurrence rule deleted", null);
    }

    private static UUID actorUserId(HttpServletRequest request) {
        Object actor = request.getAttribute(SchedulingAuthorizationInterceptor.ACTOR_USER_ID_ATTRIBUTE);
        if (actor instanceof UUID actorUserId) {
            return actorUserId;
        }
        throw new rw.rra.roomiq.common.web.DomainException(HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "Authenticated actor identity is required");
    }
}
