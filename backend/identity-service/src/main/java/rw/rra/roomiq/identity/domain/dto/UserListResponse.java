package rw.rra.roomiq.identity.domain.dto;

import java.util.List;

public record UserListResponse(
        List<UserResponse> users,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
