package rw.rra.roomiq.organization.domain.dto;

import java.util.List;

public record OrganizationPageResponse<T>(List<T> content, int page, int size,
                                          long totalElements, int totalPages,
                                          String sortBy, String sortDirection) {
}