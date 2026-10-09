package rw.rra.roomiq.scheduling.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.scheduling.domain.dto.SchedulingConstraintValidationResponse;
import rw.rra.roomiq.scheduling.domain.dto.ValidateSchedulingConstraintsRequest;
import rw.rra.roomiq.scheduling.domain.service.SchedulingConstraintValidationService;

@RestController
@RequestMapping("/api/v1/scheduling-constraints")
@Tag(name = "Scheduling constraints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Malformed interval, timezone, or recurrence request"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Scheduling access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Working calendar or recurrence rule not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                description = "Identity authorization unavailable")
})
public class SchedulingConstraintController {
    private final SchedulingConstraintValidationService service;

    public SchedulingConstraintController(SchedulingConstraintValidationService service) {
        this.service = service;
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate a reservation against Scheduling rules",
            description = "Returns valid=false for policy conflicts. Booking must proceed only when valid=true; "
                    + "request and dependency errors fail closed.")
    public ApiResponse<SchedulingConstraintValidationResponse> validate(
            @Valid @RequestBody ValidateSchedulingConstraintsRequest request) {
        return ApiResponse.success("Scheduling constraints evaluated", service.validate(request));
    }
}
