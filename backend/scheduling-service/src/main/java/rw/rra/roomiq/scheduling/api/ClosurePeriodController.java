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
import rw.rra.roomiq.scheduling.domain.dto.ClosurePeriodListQuery;
import rw.rra.roomiq.scheduling.domain.dto.ClosurePeriodPageResponse;
import rw.rra.roomiq.scheduling.domain.dto.ClosurePeriodResponse;
import rw.rra.roomiq.scheduling.domain.dto.SetClosurePeriodRequest;
import rw.rra.roomiq.scheduling.domain.service.ClosurePeriodManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/closure-periods")
@Tag(name = "Closure periods")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid closure scope or time range"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scheduling access denied"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Closure period not found"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Closure persistence conflict"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Identity authorization unavailable")
})
public class ClosurePeriodController {
    private final ClosurePeriodManagementService service;

    public ClosurePeriodController(ClosurePeriodManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List building or nationwide closure periods")
    public ApiResponse<ClosurePeriodPageResponse> list(@Valid @ModelAttribute ClosurePeriodListQuery query) {
        return ApiResponse.success("Closure periods retrieved", service.list(query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a building or nationwide closure period")
    public ApiResponse<ClosurePeriodResponse> create(@Valid @RequestBody SetClosurePeriodRequest request) {
        return ApiResponse.success("Closure period created", service.create(request));
    }

    @GetMapping("/{closurePeriodId}")
    @Operation(summary = "Get a closure period")
    public ApiResponse<ClosurePeriodResponse> get(@PathVariable UUID closurePeriodId) {
        return ApiResponse.success("Closure period retrieved", service.get(closurePeriodId));
    }

    @PutMapping("/{closurePeriodId}")
    @Operation(summary = "Update a closure period")
    public ApiResponse<ClosurePeriodResponse> update(@PathVariable UUID closurePeriodId,
            @Valid @RequestBody SetClosurePeriodRequest request) {
        return ApiResponse.success("Closure period updated", service.update(closurePeriodId, request));
    }

    @DeleteMapping("/{closurePeriodId}")
    @Operation(summary = "Delete a closure period")
    public ApiResponse<Void> delete(@PathVariable UUID closurePeriodId) {
        service.delete(closurePeriodId);
        return ApiResponse.success("Closure period deleted", null);
    }
}