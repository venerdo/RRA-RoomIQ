package rw.rra.roomiq.organization.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.organization.domain.entity.Department;
import rw.rra.roomiq.organization.domain.entity.DepartmentStatus;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select department from Department department where department.id = :id")
    java.util.Optional<Department> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select department from Department department order by department.id")
    List<Department> findAllForHierarchyUpdate();

    List<Department> findAllByOfficeBuilding_IdOrderByCodeAsc(UUID officeBuildingId);

    List<Department> findAllByParentDepartment_IdOrderByCodeAsc(UUID parentDepartmentId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByOfficeBuilding_IdAndStatus(UUID officeBuildingId, DepartmentStatus status);

    boolean existsByParentDepartment_IdAndStatus(UUID parentDepartmentId, DepartmentStatus status);

    boolean existsByOfficeBuilding_District_IdAndStatus(UUID districtId, DepartmentStatus status);

    boolean existsByOfficeBuilding_District_Province_IdAndStatus(UUID provinceId, DepartmentStatus status);

    boolean existsByOfficeBuilding_District_Province_Country_IdAndStatus(UUID countryId, DepartmentStatus status);
}