package rw.rra.roomiq.organization.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.organization.domain.entity.OfficeBuilding;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficeBuildingRepository extends JpaRepository<OfficeBuilding, UUID>, JpaSpecificationExecutor<OfficeBuilding> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select building from OfficeBuilding building where building.id = :id")
    Optional<OfficeBuilding> findByIdForUpdate(@Param("id") UUID id);

    List<OfficeBuilding> findAllByDistrict_IdOrderByNameAsc(UUID districtId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByDistrict_IdAndActiveTrue(UUID districtId);

    boolean existsByDistrict_Province_IdAndActiveTrue(UUID provinceId);

    boolean existsByDistrict_Province_Country_IdAndActiveTrue(UUID countryId);
}