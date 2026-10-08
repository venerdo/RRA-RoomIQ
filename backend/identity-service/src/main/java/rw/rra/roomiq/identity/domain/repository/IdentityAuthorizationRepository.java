package rw.rra.roomiq.identity.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.identity.domain.entity.UserRole;

import java.util.List;
import java.util.UUID;

public interface IdentityAuthorizationRepository extends Repository<UserRole, UUID> {
    @Query("select case when count(permission) > 0 then true else false end " +
            "from UserRole assignment, RolePermission rolePermission " +
            "join rolePermission.permission permission " +
            "where assignment.user.id = :userId " +
            "and assignment.role.id = rolePermission.role.id " +
            "and permission.code = :permissionCode " +
            "and ((:buildingId is null and assignment.scopeOfficeBuildingId is null) " +
            "or assignment.scopeOfficeBuildingId = :buildingId)")
    boolean hasPermissionAtBuilding(@Param("userId") UUID userId,
                                    @Param("permissionCode") String permissionCode,
                                    @Param("buildingId") UUID buildingId);

    @Query("select distinct assignment.scopeOfficeBuildingId " +
            "from UserRole assignment, RolePermission rolePermission " +
            "join rolePermission.permission permission " +
            "where assignment.user.id = :userId " +
            "and assignment.role.id = rolePermission.role.id " +
            "and permission.code = :permissionCode " +
            "and assignment.scopeOfficeBuildingId is not null")
    List<UUID> findAuthorizedBuildingScopes(@Param("userId") UUID userId,
                                            @Param("permissionCode") String permissionCode);
}
