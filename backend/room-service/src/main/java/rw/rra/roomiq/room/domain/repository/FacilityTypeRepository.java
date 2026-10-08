package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import rw.rra.roomiq.room.domain.entity.FacilityType;

import java.util.Optional;
import java.util.UUID;

public interface FacilityTypeRepository extends JpaRepository<FacilityType, UUID>, JpaSpecificationExecutor<FacilityType> {
    Optional<FacilityType> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}