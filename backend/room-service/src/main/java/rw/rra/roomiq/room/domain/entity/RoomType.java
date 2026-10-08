package rw.rra.roomiq.room.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_type")
public class RoomType extends RoomEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected RoomType() {
    }

    public RoomType(String code, String name, boolean active) {
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }

    public void update(String code, String name, boolean active) {
        this.code = code;
        this.name = name;
        this.active = active;
    }
}