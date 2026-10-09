package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import rw.rra.roomiq.booking.domain.enums.InviteStatus;
import rw.rra.roomiq.booking.domain.enums.ParticipantRole;

import java.util.UUID;

@Entity
@Table(name = "meeting_participant")
public class MeetingParticipant extends BookingEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "external_email", columnDefinition = "varchar")
    private String externalEmail;

    @Column(name = "display_name", columnDefinition = "varchar")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private ParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "invite_status", nullable = false, columnDefinition = "varchar")
    private InviteStatus inviteStatus;

    protected MeetingParticipant() {
    }

    public MeetingParticipant(Meeting meeting, UUID userId, String externalEmail, String displayName,
                              ParticipantRole role, InviteStatus inviteStatus) {
        this.meeting = meeting;
        this.userId = userId;
        this.externalEmail = externalEmail;
        this.displayName = displayName;
        this.role = role;
        this.inviteStatus = inviteStatus;
    }

    public Meeting getMeeting() { return meeting; }
    public UUID getUserId() { return userId; }
    public String getExternalEmail() { return externalEmail; }
    public String getDisplayName() { return displayName; }
    public ParticipantRole getRole() { return role; }
    public InviteStatus getInviteStatus() { return inviteStatus; }
}
