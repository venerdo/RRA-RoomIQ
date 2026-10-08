package rw.rra.roomiq.room.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "room_rule_allowed_department", uniqueConstraints = @UniqueConstraint(
        name = "uq_room_rule_allowed_department", columnNames = {"room_rule_id", "department_id"}))
public class RoomRuleAllowedDepartment extends RoomEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_rule_id", nullable = false)
    private RoomRule roomRule;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    protected RoomRuleAllowedDepartment() {
    }

    public RoomRuleAllowedDepartment(RoomRule roomRule, UUID departmentId) {
        this.roomRule = roomRule;
        this.departmentId = departmentId;
    }

    public RoomRule getRoomRule() { return roomRule; }
    public UUID getDepartmentId() { return departmentId; }
}