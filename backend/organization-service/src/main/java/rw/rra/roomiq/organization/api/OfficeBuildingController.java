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
import rw.rra.roomiq.organization.domain.dto.CreateOfficeBuildingRequest;
import rw.rra.roomiq.organization.domain.dto.OfficeBuildingResponse;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageQuery;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageResponse;
import rw.rra.roomiq.organization.domain.dto.SetActiveRequest;
import rw.rra.roomiq.organization.domain.service.OrganizationManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/office-buildings")
@Tag(name = "Organization - office buildings")
public class OfficeBuildingController {
    private final OrganizationManagementService service;

    public OfficeBuildingController(OrganizationManagementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an office building")
    public ApiResponse<OfficeBuildingResponse> create(@Valid @RequestBody CreateOfficeBuildingRequest request) {
        return ApiResponse.success("Office building created", service.createOfficeBuilding(request));
    }

    @GetMapping
    @Operation(summary = "Search and page office buildings")
    public ApiResponse<OrganizationPageResponse<OfficeBuildingResponse>> list(
            @Valid @ModelAttribute OrganizationPageQuery query) {
        return ApiResponse.success("Office buildings retrieved", service.listOfficeBuildings(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an office building")
    public ApiResponse<OfficeBuildingResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("Office building retrieved", service.getOfficeBuilding(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an office building")
    public ApiResponse<OfficeBuildingResponse> update(@PathVariable UUID id,
                                                       @Valid @RequestBody CreateOfficeBuildingRequest request) {
        return ApiResponse.success("Office building updated", service.updateOfficeBuilding(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate an office building")
    public ApiResponse<OfficeBuildingResponse> setActive(@PathVariable UUID id,
                                                          @Valid @RequestBody SetActiveRequest request) {
        return ApiResponse.success("Office building status updated",
                service.setOfficeBuildingActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unreferenced office building")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.deleteOfficeBuilding(id);
        return ApiResponse.success("Office building deleted", null);
    }
}