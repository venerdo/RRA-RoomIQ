package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.room.domain.entity.RoomPhoto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomPhotoRepository extends JpaRepository<RoomPhoto, UUID> {
	List<RoomPhoto> findAllByRoom_IdOrderBySortOrderAscCreatedAtAsc(UUID roomId);

	Optional<RoomPhoto> findByIdAndRoom_Id(UUID photoId, UUID roomId);

	long countByRoom_Id(UUID roomId);
}