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
import rw.rra.roomiq.organization.domain.dto.CreateDepartmentRequest;
import rw.rra.roomiq.organization.domain.dto.DepartmentResponse;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageQuery;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageResponse;
import rw.rra.roomiq.organization.domain.dto.SetDepartmentParentRequest;
import rw.rra.roomiq.organization.domain.dto.SetDepartmentStatusRequest;
import rw.rra.roomiq.organization.domain.service.OrganizationManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "Organization - departments")
public class DepartmentController {
    private final OrganizationManagementService service;

    public DepartmentController(OrganizationManagementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a department")
    public ApiResponse<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.success("Department created", service.createDepartment(request));
    }

    @GetMapping
    @Operation(summary = "Search and page departments")
    public ApiResponse<OrganizationPageResponse<DepartmentResponse>> list(
            @Valid @ModelAttribute OrganizationPageQuery query) {
        return ApiResponse.success("Departments retrieved", service.listDepartments(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a department")
    public ApiResponse<DepartmentResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("Department retrieved", service.getDepartment(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a department and its parent relationship")
    public ApiResponse<DepartmentResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.success("Department updated", service.updateDepartment(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a department")
    public ApiResponse<DepartmentResponse> setStatus(@PathVariable UUID id,
                                                      @Valid @RequestBody SetDepartmentStatusRequest request) {
        return ApiResponse.success("Department status updated", service.setDepartmentStatus(id, request.status()));
    }

    @PatchMapping("/{id}/parent")
    @Operation(summary = "Reparent a department")
    public ApiResponse<DepartmentResponse> setParent(@PathVariable UUID id,
                                                      @Valid @RequestBody SetDepartmentParentRequest request) {
        return ApiResponse.success("Department parent updated",
                service.setDepartmentParent(id, request.parentDepartmentId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a department without children or references")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.deleteDepartment(id);
        return ApiResponse.success("Department deleted", null);
    }
}