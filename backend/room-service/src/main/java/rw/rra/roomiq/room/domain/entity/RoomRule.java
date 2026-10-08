package rw.rra.roomiq.room.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_rule")
public class RoomRule extends RoomEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "office_building_id")
    private UUID officeBuildingId;

    @Column(name = "min_duration_minutes")
    private Integer minDurationMinutes;

    @Column(name = "max_duration_minutes")
    private Integer maxDurationMinutes;

    @Column(name = "min_advance_minutes")
    private Integer minAdvanceMinutes;

    @Column(name = "max_advance_days")
    private Integer maxAdvanceDays;

    @Column(name = "cancellation_deadline_minutes")
    private Integer cancellationDeadlineMinutes;

    @Column(name = "recurring_allowed", nullable = false)
    private boolean recurringAllowed;

    @Column(name = "external_guests_allowed", nullable = false)
    private boolean externalGuestsAllowed;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    @Column(name = "outside_hours_allowed", nullable = false)
    private boolean outsideHoursAllowed;

    @Column(name = "release_buffer_minutes", nullable = false)
    private int releaseBufferMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    protected RoomRule() {
    }

    public RoomRule(Room room, UUID officeBuildingId, Integer minDurationMinutes, Integer maxDurationMinutes,
                    Integer minAdvanceMinutes, Integer maxAdvanceDays, Integer cancellationDeadlineMinutes,
                    boolean recurringAllowed, boolean externalGuestsAllowed, boolean approvalRequired,
                    boolean outsideHoursAllowed, int releaseBufferMinutes, boolean active, Instant effectiveFrom) {
        this.room = room;
        this.officeBuildingId = officeBuildingId;
        this.minDurationMinutes = minDurationMinutes;
        this.maxDurationMinutes = maxDurationMinutes;
        this.minAdvanceMinutes = minAdvanceMinutes;
        this.maxAdvanceDays = maxAdvanceDays;
        this.cancellationDeadlineMinutes = cancellationDeadlineMinutes;
        this.recurringAllowed = recurringAllowed;
        this.externalGuestsAllowed = externalGuestsAllowed;
        this.approvalRequired = approvalRequired;
        this.outsideHoursAllowed = outsideHoursAllowed;
        this.releaseBufferMinutes = releaseBufferMinutes;
        this.active = active;
        this.effectiveFrom = effectiveFrom;
    }

    public Room getRoom() { return room; }
    public UUID getOfficeBuildingId() { return officeBuildingId; }
    public Integer getMinDurationMinutes() { return minDurationMinutes; }
    public Integer getMaxDurationMinutes() { return maxDurationMinutes; }
    public Integer getMinAdvanceMinutes() { return minAdvanceMinutes; }
    public Integer getMaxAdvanceDays() { return maxAdvanceDays; }
    public Integer getCancellationDeadlineMinutes() { return cancellationDeadlineMinutes; }
    public boolean isRecurringAllowed() { return recurringAllowed; }
    public boolean isExternalGuestsAllowed() { return externalGuestsAllowed; }
    public boolean isApprovalRequired() { return approvalRequired; }
    public boolean isOutsideHoursAllowed() { return outsideHoursAllowed; }
    public int getReleaseBufferMinutes() { return releaseBufferMinutes; }
    public boolean isActive() { return active; }
    public Instant getEffectiveFrom() { return effectiveFrom; }

    public void updateDetails(Integer minDurationMinutes, Integer maxDurationMinutes,
                              Integer minAdvanceMinutes, Integer maxAdvanceDays,
                              Integer cancellationDeadlineMinutes, boolean recurringAllowed,
                              boolean externalGuestsAllowed, boolean approvalRequired,
                              boolean outsideHoursAllowed, int releaseBufferMinutes,
                              boolean active, Instant effectiveFrom) {
        this.minDurationMinutes = minDurationMinutes;
        this.maxDurationMinutes = maxDurationMinutes;
        this.minAdvanceMinutes = minAdvanceMinutes;
        this.maxAdvanceDays = maxAdvanceDays;
        this.cancellationDeadlineMinutes = cancellationDeadlineMinutes;
        this.recurringAllowed = recurringAllowed;
        this.externalGuestsAllowed = externalGuestsAllowed;
        this.approvalRequired = approvalRequired;
        this.outsideHoursAllowed = outsideHoursAllowed;
        this.releaseBufferMinutes = releaseBufferMinutes;
        this.active = active;
        this.effectiveFrom = effectiveFrom;
    }
}