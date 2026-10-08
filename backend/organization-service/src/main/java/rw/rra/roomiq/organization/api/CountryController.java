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
import rw.rra.roomiq.organization.domain.dto.CountryHierarchyResponse;
import rw.rra.roomiq.organization.domain.dto.CountryResponse;
import rw.rra.roomiq.organization.domain.dto.CreateCountryRequest;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageQuery;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageResponse;
import rw.rra.roomiq.organization.domain.dto.SetActiveRequest;
import rw.rra.roomiq.organization.domain.service.OrganizationManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/countries")
@Tag(name = "Organization - countries")
public class CountryController {
    private final OrganizationManagementService service;

    public CountryController(OrganizationManagementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a country")
    public ApiResponse<CountryResponse> create(@Valid @RequestBody CreateCountryRequest request) {
        return ApiResponse.success("Country created", service.createCountry(request));
    }

    @GetMapping
    @Operation(summary = "Search and page countries")
    public ApiResponse<OrganizationPageResponse<CountryResponse>> list(
            @Valid @ModelAttribute OrganizationPageQuery query) {
        return ApiResponse.success("Countries retrieved", service.listCountries(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a country")
    public ApiResponse<CountryResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("Country retrieved", service.getCountry(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a country")
    public ApiResponse<CountryResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody CreateCountryRequest request) {
        return ApiResponse.success("Country updated", service.updateCountry(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a country")
    public ApiResponse<CountryResponse> setActive(@PathVariable UUID id,
                                                    @Valid @RequestBody SetActiveRequest request) {
        return ApiResponse.success("Country status updated", service.setCountryActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an unreferenced country")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.deleteCountry(id);
        return ApiResponse.success("Country deleted", null);
    }

    @GetMapping("/{id}/hierarchy")
    @Operation(summary = "Get a country with its organization hierarchy")
    public ApiResponse<CountryHierarchyResponse> hierarchy(@PathVariable UUID id) {
        return ApiResponse.success("Country hierarchy retrieved", service.getCountryHierarchy(id));
    }
}