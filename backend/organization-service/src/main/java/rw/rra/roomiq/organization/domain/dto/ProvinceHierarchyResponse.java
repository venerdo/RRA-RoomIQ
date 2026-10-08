package rw.rra.roomiq.organization.domain.dto;

import java.util.List;

public record ProvinceHierarchyResponse(ProvinceResponse province, List<DistrictHierarchyResponse> districts) {
}