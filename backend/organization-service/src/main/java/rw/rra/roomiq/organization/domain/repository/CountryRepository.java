package rw.rra.roomiq.organization.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.organization.domain.entity.Country;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface CountryRepository extends JpaRepository<Country, UUID>, JpaSpecificationExecutor<Country> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select country from Country country where country.id = :id")
    Optional<Country> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByIsoCode(String isoCode);

    Optional<Country> findByIsoCode(String isoCode);
}