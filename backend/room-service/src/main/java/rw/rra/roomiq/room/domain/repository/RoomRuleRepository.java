package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.room.domain.entity.RoomRule;

import java.util.List;
import java.util.UUID;

public interface RoomRuleRepository extends JpaRepository<RoomRule, UUID> {
	List<RoomRule> findAllByRoom_IdOrderByEffectiveFromDesc(UUID roomId);

	List<RoomRule> findAllByRoomIsNullAndOfficeBuildingIdOrderByEffectiveFromDesc(UUID officeBuildingId);
}