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
import rw.rra.roomiq.organization.domain.dto.CreateProvinceRequest;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageQuery;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageResponse;
import rw.rra.roomiq.organization.domain.dto.ProvinceResponse;
import rw.rra.roomiq.organization.domain.dto.SetActiveRequest;
import rw.rra.roomiq.organization.domain.service.OrganizationManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/provinces")
@Tag(name = "Organization - provinces")
public class ProvinceController {
    private final OrganizationManagementService service;

    public ProvinceController(OrganizationManagementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a province")
    public ApiResponse<ProvinceResponse> create(@Valid @RequestBody CreateProvinceRequest request) {
        return ApiResponse.success("Province created", service.createProvince(request));
    }

    @GetMapping
    @Operation(summary = "Search and page provinces")
    public ApiResponse<OrganizationPageResponse<ProvinceResponse>> list(
            @Valid @ModelAttribute OrganizationPageQuery query) {
        return ApiResponse.success("Provinces retrieved", service.listProvinces(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a province")
    public ApiResponse<ProvinceResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("Province retrieved", service.getProvince(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a province")
    public ApiResponse<ProvinceResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody CreateProvinceRequest request) {
        return ApiResponse.success("Province updated", service.updateProvince(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a province")
    public ApiResponse<ProvinceResponse> setActive(@PathVariable UUID id,
                                                    @Valid @RequestBody SetActiveRequest request) {
        return ApiResponse.success("Province status updated", service.setProvinceActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unreferenced province")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.deleteProvince(id);
        return ApiResponse.success("Province deleted", null);
    }
}