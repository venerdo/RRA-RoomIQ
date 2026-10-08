package rw.rra.roomiq.organization.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.organization.domain.dto.CreateFloorRequest;
import rw.rra.roomiq.organization.domain.dto.FloorResponse;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageQuery;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageResponse;
import rw.rra.roomiq.organization.domain.dto.SetActiveRequest;
import rw.rra.roomiq.organization.domain.service.OrganizationManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/floors")
@Tag(name = "Organization - floors")
public class FloorController {
    private final OrganizationManagementService service;

    public FloorController(OrganizationManagementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a floor")
    public ApiResponse<FloorResponse> create(@Valid @RequestBody CreateFloorRequest request) {
        return ApiResponse.success("Floor created", service.createFloor(request));
    }

    @GetMapping
    @Operation(summary = "Search and page floors")
    public ApiResponse<OrganizationPageResponse<FloorResponse>> list(
            @Valid @ModelAttribute OrganizationPageQuery query) {
        return ApiResponse.success("Floors retrieved", service.listFloors(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a floor")
    public ApiResponse<FloorResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("Floor retrieved", service.getFloor(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a floor")
    public ApiResponse<FloorResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody CreateFloorRequest request) {
        return ApiResponse.success("Floor updated", service.updateFloor(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a floor")
    public ApiResponse<FloorResponse> setActive(@PathVariable UUID id,
                                                 @Valid @RequestBody SetActiveRequest request) {
        return ApiResponse.success("Floor status updated", service.setFloorActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unreferenced floor")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.deleteFloor(id);
        return ApiResponse.success("Floor deleted", null);
    }
}