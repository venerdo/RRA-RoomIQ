package rw.rra.roomiq.scheduling.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import rw.rra.roomiq.scheduling.domain.entity.ClosurePeriod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClosurePeriodRepository extends JpaRepository<ClosurePeriod, UUID>,
	JpaSpecificationExecutor<ClosurePeriod> {
    @Query(value = """
            SELECT lower(period) AS "startsAt", upper(period) AS "endsAt"
            FROM closure_period
            WHERE blocks_booking = TRUE
              AND (office_building_id IS NULL OR office_building_id = :officeBuildingId)
              AND period && tstzrange(CAST(:startsAt AS timestamptz),
                                      CAST(:endsAt AS timestamptz), '[)')
            """, nativeQuery = true)
    List<BlockingClosureInterval> findBlockingOverlaps(
            @Param("officeBuildingId") UUID officeBuildingId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM closure_period
                WHERE blocks_booking = TRUE
                  AND (office_building_id IS NULL OR office_building_id = :officeBuildingId)
                  AND period && tstzrange(CAST(:startsAt AS timestamptz),
                                          CAST(:endsAt AS timestamptz), '[)')
            )
            """, nativeQuery = true)
    boolean existsBlockingOverlap(@Param("officeBuildingId") UUID officeBuildingId,
                                  @Param("startsAt") Instant startsAt,
                                  @Param("endsAt") Instant endsAt);
}