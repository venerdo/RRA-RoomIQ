package rw.rra.roomiq.room.integration;

import java.util.UUID;

public interface OrganizationDirectoryClient {
    void validateOfficeBuilding(UUID officeBuildingId);

    void validateBuildingAndFloor(UUID officeBuildingId, UUID floorId);

    void validateDepartment(UUID departmentId, UUID expectedOfficeBuildingId);
}