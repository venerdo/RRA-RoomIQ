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
import rw.rra.roomiq.scheduling.domain.dto.AvailabilitySearchRequest;
import rw.rra.roomiq.scheduling.domain.dto.AvailabilitySearchResponse;
import rw.rra.roomiq.scheduling.domain.service.AvailabilitySearchService;

@RestController
@RequestMapping("/api/v1/availability")
@Tag(name = "Availability")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Invalid search range, filters, or recurrence rule"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Authentication required"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Scheduling access denied"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Working calendar or recurrence rule not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                description = "Room or authoritative Booking occupancy dependency is unavailable")
})
public class AvailabilityController {
    private final AvailabilitySearchService service;

    public AvailabilityController(AvailabilitySearchService service) {
        this.service = service;
    }

    @PostMapping("/search")
    @Operation(summary = "Search continuous candidate room availability",
            description = "Returns candidate intervals only, never a reservation confirmation. Candidate data is "
                    + "omitted when authoritative Room or Booking occupancy inputs are unavailable.")
    public ApiResponse<AvailabilitySearchResponse> search(
            @Valid @RequestBody AvailabilitySearchRequest request) {
        return ApiResponse.success("Availability candidates retrieved", service.search(request));
    }
}
