package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meeting_share_link")
public class MeetingShareLink extends BookingEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "varchar")
    private String tokenHash;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    protected MeetingShareLink() {
    }

    public MeetingShareLink(Meeting meeting, String tokenHash, UUID createdByUserId,
                            Instant expiresAt, Instant revokedAt, int viewCount) {
        this.meeting = meeting;
        this.tokenHash = tokenHash;
        this.createdByUserId = createdByUserId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.viewCount = viewCount;
    }

    public Meeting getMeeting() { return meeting; }
    public String getTokenHash() { return tokenHash; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public int getViewCount() { return viewCount; }
}
