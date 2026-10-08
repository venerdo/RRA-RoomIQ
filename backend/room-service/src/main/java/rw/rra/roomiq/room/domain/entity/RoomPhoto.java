package rw.rra.roomiq.room.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "room_photo")
public class RoomPhoto extends RoomEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "cloudinary_public_id", nullable = false, unique = true, length = 255)
    private String cloudinaryPublicId;

    @Column(name = "secure_url", nullable = false, columnDefinition = "text")
    private String secureUrl;

    @Column(name = "sort_order")
    private Short sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "approved_for_public", nullable = false)
    private boolean approvedForPublic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RoomPhoto() {
    }

    public RoomPhoto(Room room, String cloudinaryPublicId, String secureUrl, Short sortOrder,
                     boolean primary, boolean approvedForPublic) {
        this.room = room;
        this.cloudinaryPublicId = cloudinaryPublicId;
        this.secureUrl = secureUrl;
        this.sortOrder = sortOrder;
        this.primary = primary;
        this.approvedForPublic = approvedForPublic;
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Room getRoom() { return room; }
    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public String getSecureUrl() { return secureUrl; }
    public Short getSortOrder() { return sortOrder; }
    public boolean isPrimary() { return primary; }
    public boolean isApprovedForPublic() { return approvedForPublic; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateMetadata(Short sortOrder, boolean primary, boolean approvedForPublic) {
        this.sortOrder = sortOrder;
        this.primary = primary;
        this.approvedForPublic = approvedForPublic;
    }
}