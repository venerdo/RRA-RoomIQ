package rw.rra.roomiq.organization.domain.dto;

import java.util.List;

public record CountryHierarchyResponse(CountryResponse country, List<ProvinceHierarchyResponse> provinces) {
}