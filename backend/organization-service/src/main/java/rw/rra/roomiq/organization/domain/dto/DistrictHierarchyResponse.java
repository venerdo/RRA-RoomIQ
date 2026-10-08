package rw.rra.roomiq.organization.domain.dto;

import java.util.List;

public record DistrictHierarchyResponse(DistrictResponse district,
                                        List<OfficeBuildingHierarchyResponse> officeBuildings) {
}