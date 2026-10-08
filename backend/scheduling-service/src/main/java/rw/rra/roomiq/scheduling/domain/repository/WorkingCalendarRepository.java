package rw.rra.roomiq.scheduling.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;

import java.util.UUID;

public interface WorkingCalendarRepository extends JpaRepository<WorkingCalendar, UUID>,
    JpaSpecificationExecutor<WorkingCalendar> {
    Page<WorkingCalendar> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<WorkingCalendar> findByOfficeBuildingId(UUID officeBuildingId, Pageable pageable);
    Page<WorkingCalendar> findByActive(boolean active, Pageable pageable);
    Page<WorkingCalendar> findByNameContainingIgnoreCaseAndOfficeBuildingId(String name,
	    UUID officeBuildingId, Pageable pageable);
    Page<WorkingCalendar> findByNameContainingIgnoreCaseAndActive(String name, boolean active,
	    Pageable pageable);
    Page<WorkingCalendar> findByOfficeBuildingIdAndActive(UUID officeBuildingId, boolean active,
	    Pageable pageable);
    Page<WorkingCalendar> findByNameContainingIgnoreCaseAndOfficeBuildingIdAndActive(String name,
	    UUID officeBuildingId, boolean active, Pageable pageable);
    boolean existsByOfficeBuildingId(UUID officeBuildingId);
    boolean existsByOfficeBuildingIdAndIdNot(UUID officeBuildingId, UUID id);
}