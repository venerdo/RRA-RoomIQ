package rw.rra.roomiq.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.identity.domain.entity.UserPrivilege;

import java.util.List;
import java.util.UUID;

public interface UserPrivilegeRepository extends JpaRepository<UserPrivilege, UUID> {
    List<UserPrivilege> findAllByUserIdAndActiveTrue(UUID userId);

    List<UserPrivilege> findAllByUserIdAndPrivilegeCodeAndActiveTrue(UUID userId, String privilegeCode);

    List<UserPrivilege> findAllByUserIdAndPrivilegeCodeIgnoreCase(UUID userId, String privilegeCode);

    List<UserPrivilege> findAllByUserIdAndPrivilegeCodeIgnoreCaseAndActiveTrue(UUID userId, String privilegeCode);
}
