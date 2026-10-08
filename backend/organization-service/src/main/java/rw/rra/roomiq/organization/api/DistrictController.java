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
import rw.rra.roomiq.organization.domain.dto.CreateDistrictRequest;
import rw.rra.roomiq.organization.domain.dto.DistrictResponse;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageQuery;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageResponse;
import rw.rra.roomiq.organization.domain.dto.SetActiveRequest;
import rw.rra.roomiq.organization.domain.service.OrganizationManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/districts")
@Tag(name = "Organization - districts")
public class DistrictController {
    private final OrganizationManagementService service;

    public DistrictController(OrganizationManagementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a district")
    public ApiResponse<DistrictResponse> create(@Valid @RequestBody CreateDistrictRequest request) {
        return ApiResponse.success("District created", service.createDistrict(request));
    }

    @GetMapping
    @Operation(summary = "Search and page districts")
    public ApiResponse<OrganizationPageResponse<DistrictResponse>> list(
            @Valid @ModelAttribute OrganizationPageQuery query) {
        return ApiResponse.success("Districts retrieved", service.listDistricts(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a district")
    public ApiResponse<DistrictResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("District retrieved", service.getDistrict(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a district")
    public ApiResponse<DistrictResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody CreateDistrictRequest request) {
        return ApiResponse.success("District updated", service.updateDistrict(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a district")
    public ApiResponse<DistrictResponse> setActive(@PathVariable UUID id,
                                                    @Valid @RequestBody SetActiveRequest request) {
        return ApiResponse.success("District status updated", service.setDistrictActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unreferenced district")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.deleteDistrict(id);
        return ApiResponse.success("District deleted", null);
    }
}