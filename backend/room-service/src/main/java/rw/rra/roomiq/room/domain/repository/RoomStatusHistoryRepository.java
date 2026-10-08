package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.room.domain.entity.RoomStatusHistory;

import java.util.List;
import java.util.UUID;

public interface RoomStatusHistoryRepository extends JpaRepository<RoomStatusHistory, UUID> {
	List<RoomStatusHistory> findAllByRoom_IdOrderByChangedAtAsc(UUID roomId);
}