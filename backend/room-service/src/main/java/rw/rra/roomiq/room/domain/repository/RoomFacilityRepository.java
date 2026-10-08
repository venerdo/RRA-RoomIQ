package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.room.domain.entity.RoomFacility;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomFacilityRepository extends JpaRepository<RoomFacility, UUID> {
	List<RoomFacility> findAllByRoom_IdOrderByFacilityType_NameAsc(UUID roomId);

	Optional<RoomFacility> findByIdAndRoom_Id(UUID id, UUID roomId);

	boolean existsByRoom_IdAndFacilityType_Id(UUID roomId, UUID facilityTypeId);

	boolean existsByRoom_IdAndFacilityType_IdAndIdNot(UUID roomId, UUID facilityTypeId, UUID id);
}