package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.room.domain.entity.RoomRuleAllowedDepartment;

import java.util.List;
import java.util.UUID;

public interface RoomRuleAllowedDepartmentRepository extends JpaRepository<RoomRuleAllowedDepartment, UUID> {
	List<RoomRuleAllowedDepartment> findAllByRoomRule_IdOrderByDepartmentIdAsc(UUID roomRuleId);

	void deleteAllByRoomRule_Id(UUID roomRuleId);
}