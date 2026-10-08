package rw.rra.roomiq.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.identity.domain.entity.RolePermission;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    List<RolePermission> findAllByRoleId(UUID roleId);

    long deleteByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
}
