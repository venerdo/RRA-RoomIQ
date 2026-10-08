package rw.rra.roomiq.room.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.room.domain.dto.CatalogPageResponse;
import rw.rra.roomiq.room.domain.dto.CreateFacilityTypeRequest;
import rw.rra.roomiq.room.domain.dto.FacilityTypeDto;
import rw.rra.roomiq.room.domain.dto.FacilityTypePageQuery;
import rw.rra.roomiq.room.domain.service.RoomManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facility-types")
@Tag(name = "Facility types")
public class FacilityTypeController {
    private final RoomManagementService service;

    public FacilityTypeController(RoomManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List facility types")
    public ApiResponse<CatalogPageResponse<FacilityTypeDto>> list(@Valid @ModelAttribute FacilityTypePageQuery query) {
        return ApiResponse.success("Facility types retrieved", service.listFacilityTypes(query));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a facility type")
    public ApiResponse<FacilityTypeDto> create(@Valid @RequestBody CreateFacilityTypeRequest request) {
        return ApiResponse.success("Facility type created", service.createFacilityType(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a facility type")
    public ApiResponse<FacilityTypeDto> get(@PathVariable UUID id) {
        return ApiResponse.success("Facility type retrieved", service.getFacilityType(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a facility type")
    public ApiResponse<FacilityTypeDto> update(@PathVariable UUID id,
                                             @Valid @RequestBody CreateFacilityTypeRequest request) {
        return ApiResponse.success("Facility type updated", service.updateFacilityType(id, request));
    }
}
