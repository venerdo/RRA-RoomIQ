package rw.rra.roomiq.organization.domain.dto;

import java.util.List;

public record OfficeBuildingHierarchyResponse(OfficeBuildingResponse officeBuilding,
                                             List<FloorResponse> floors,
                                             List<DepartmentHierarchyResponse> departments) {
}