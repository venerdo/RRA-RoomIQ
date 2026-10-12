package rw.rra.roomiq.booking.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.booking.domain.entity.MeetingShareLink;

import java.util.Optional;
import java.util.UUID;

public interface MeetingShareLinkRepository extends JpaRepository<MeetingShareLink, UUID> {
    Optional<MeetingShareLink> findByCreatedByUserIdAndIdempotencyKey(UUID createdByUserId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select link from MeetingShareLink link where link.tokenHash = :tokenHash")
    Optional<MeetingShareLink> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select link from MeetingShareLink link where link.id = :id")
    Optional<MeetingShareLink> findByIdForUpdate(@Param("id") UUID id);
}
