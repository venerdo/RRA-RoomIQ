package rw.rra.roomiq.scheduling.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.postgresql.util.PGobject;
import rw.rra.roomiq.scheduling.domain.type.PostgreSqlTstzRangeType;

import java.util.UUID;

@Entity
@Table(name = "closure_period")
public class ClosurePeriod extends SchedulingEntity {
    @Column(name = "office_building_id")
    private UUID officeBuildingId;

    @Type(PostgreSqlTstzRangeType.class)
    @Column(nullable = false, columnDefinition = "tstzrange")
    private PGobject period;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "blocks_booking", nullable = false)
    private boolean blocksBooking;

    protected ClosurePeriod() {
    }

    public ClosurePeriod(UUID officeBuildingId, PGobject period, String reason, boolean blocksBooking) {
        this.officeBuildingId = officeBuildingId;
        this.period = period;
        this.reason = reason;
        this.blocksBooking = blocksBooking;
    }

    public UUID getOfficeBuildingId() { return officeBuildingId; }
    public PGobject getPeriod() { return period; }
    public String getReason() { return reason; }
    public boolean isBlocksBooking() { return blocksBooking; }

    public void update(UUID officeBuildingId, PGobject period, String reason, boolean blocksBooking) {
        this.officeBuildingId = officeBuildingId;
        this.period = period;
        this.reason = reason;
        this.blocksBooking = blocksBooking;
    }
}