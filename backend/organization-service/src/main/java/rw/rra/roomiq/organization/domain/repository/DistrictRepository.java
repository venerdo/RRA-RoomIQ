package rw.rra.roomiq.organization.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.organization.domain.entity.District;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DistrictRepository extends JpaRepository<District, UUID>, JpaSpecificationExecutor<District> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select district from District district where district.id = :id")
    Optional<District> findByIdForUpdate(@Param("id") UUID id);

    List<District> findAllByProvince_IdOrderByNameAsc(UUID provinceId);

    boolean existsByProvince_IdAndNameIgnoreCase(UUID provinceId, String name);

    boolean existsByProvince_IdAndActiveTrue(UUID provinceId);

    boolean existsByProvince_Country_IdAndActiveTrue(UUID countryId);
}