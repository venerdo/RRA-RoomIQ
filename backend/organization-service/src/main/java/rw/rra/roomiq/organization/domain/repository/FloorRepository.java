package rw.rra.roomiq.organization.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.organization.domain.entity.Floor;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FloorRepository extends JpaRepository<Floor, UUID>, JpaSpecificationExecutor<Floor> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select floor from Floor floor where floor.id = :id")
    Optional<Floor> findByIdForUpdate(@Param("id") UUID id);

    List<Floor> findAllByOfficeBuilding_IdOrderByLevelAsc(UUID officeBuildingId);

    boolean existsByOfficeBuilding_IdAndNameIgnoreCase(UUID officeBuildingId, String name);

    boolean existsByOfficeBuilding_IdAndActiveTrue(UUID officeBuildingId);

    boolean existsByOfficeBuilding_District_IdAndActiveTrue(UUID districtId);

    boolean existsByOfficeBuilding_District_Province_IdAndActiveTrue(UUID provinceId);

    boolean existsByOfficeBuilding_District_Province_Country_IdAndActiveTrue(UUID countryId);
}