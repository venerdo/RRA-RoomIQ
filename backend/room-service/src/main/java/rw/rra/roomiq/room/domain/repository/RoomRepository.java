package rw.rra.roomiq.room.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import rw.rra.roomiq.room.domain.entity.Room;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {
    boolean existsByOfficeBuildingIdAndCodeIgnoreCase(UUID officeBuildingId, String code);

    boolean existsByOfficeBuildingIdAndCodeIgnoreCaseAndIdNot(UUID officeBuildingId, String code, UUID id);

    boolean existsByOfficeBuildingIdAndNameIgnoreCase(UUID officeBuildingId, String name);

    boolean existsByOfficeBuildingIdAndNameIgnoreCaseAndIdNot(UUID officeBuildingId, String name, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from Room room where room.id = :roomId")
    Optional<Room> findByIdForPhotoManagement(@Param("roomId") UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from Room room where room.id = :roomId and room.deletedAt is null")
    Optional<Room> findActiveByIdForUpdate(@Param("roomId") UUID roomId);
}