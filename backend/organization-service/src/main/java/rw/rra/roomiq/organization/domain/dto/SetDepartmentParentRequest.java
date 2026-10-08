package rw.rra.roomiq.organization.domain.dto;

import java.util.UUID;

public record SetDepartmentParentRequest(UUID parentDepartmentId) {
}