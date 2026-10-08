package rw.rra.roomiq.organization.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.organization.domain.entity.Province;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvinceRepository extends JpaRepository<Province, UUID>, JpaSpecificationExecutor<Province> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select province from Province province where province.id = :id")
    Optional<Province> findByIdForUpdate(@Param("id") UUID id);

    List<Province> findAllByCountry_IdOrderByNameAsc(UUID countryId);

    boolean existsByCountry_IdAndNameIgnoreCase(UUID countryId, String name);

    boolean existsByCountry_IdAndActiveTrue(UUID countryId);
}