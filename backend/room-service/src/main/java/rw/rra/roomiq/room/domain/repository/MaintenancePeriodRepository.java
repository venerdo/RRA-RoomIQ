package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import rw.rra.roomiq.room.domain.entity.MaintenancePeriod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenancePeriodRepository extends JpaRepository<MaintenancePeriod, UUID> {
	@Query(value = "select * from maintenance_period where room_id = :roomId order by lower(period)",
			nativeQuery = true)
	List<MaintenancePeriod> findAllByRoomIdOrderByStart(@org.springframework.data.repository.query.Param("roomId") UUID roomId);

	Optional<MaintenancePeriod> findByIdAndRoom_Id(UUID id, UUID roomId);
}