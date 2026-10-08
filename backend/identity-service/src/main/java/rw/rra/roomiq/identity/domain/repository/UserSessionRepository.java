package rw.rra.roomiq.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.identity.domain.entity.UserSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UserSession session join fetch session.user where session.refreshTokenHash = :tokenHash")
    Optional<UserSession> findByRefreshTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("select count(session) > 0 from UserSession session where session.id = :id " +
            "and session.user.id = :userId " +
            "and session.revokedAt is null and session.expiresAt > :now and session.user.status = " +
            "rw.rra.roomiq.identity.domain.entity.UserStatus.ACTIVE")
    boolean existsActiveSessionForUser(@Param("id") UUID id, @Param("userId") UUID userId,
                                       @Param("now") Instant now);

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant currentTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UserSession session join fetch session.user where session.user.id = :userId " +
            "and session.revokedAt is null and session.expiresAt > :now")
    List<UserSession> findActiveSessionsForUpdate(@Param("userId") UUID userId, @Param("now") Instant now);
}
