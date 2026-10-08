package rw.rra.roomiq.organization.domain.dto;

import java.util.List;

public record DepartmentHierarchyResponse(DepartmentResponse department,
                                         List<DepartmentHierarchyResponse> children) {
}