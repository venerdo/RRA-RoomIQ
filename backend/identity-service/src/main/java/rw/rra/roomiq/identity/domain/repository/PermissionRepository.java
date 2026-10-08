package rw.rra.roomiq.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.identity.domain.entity.Permission;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCodeIgnoreCase(String code);
}
