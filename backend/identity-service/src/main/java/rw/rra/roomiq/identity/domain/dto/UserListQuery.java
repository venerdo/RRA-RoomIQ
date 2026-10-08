package rw.rra.roomiq.identity.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import rw.rra.roomiq.identity.domain.entity.UserStatus;

import java.util.UUID;

public record UserListQuery(
        @Size(max = 200) String search,
        UserStatus status,
        UUID departmentId,
        UUID officeBuildingId,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    public UserListQuery {
        if (page == null) {
            page = 0;
        }
        if (size == null || size == 0) {
            size = 20;
        }
    }
}
