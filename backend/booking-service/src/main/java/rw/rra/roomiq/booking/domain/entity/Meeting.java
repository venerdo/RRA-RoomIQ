package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import rw.rra.roomiq.booking.domain.enums.MeetingVisibility;

import java.time.Instant;

@Entity
@Table(name = "meeting")
public class Meeting extends BookingEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false, columnDefinition = "varchar")
    private String title;

    @Column(columnDefinition = "text")
    private String agenda;

    @Column(name = "organizer_display_name", nullable = false, columnDefinition = "varchar")
    private String organizerDisplayName;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "contact_info", columnDefinition = "varchar")
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private MeetingVisibility visibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Meeting() {
    }

    public Meeting(Reservation reservation, String title, String agenda, String organizerDisplayName,
                   String instructions, String contactInfo, MeetingVisibility visibility, Instant createdAt) {
        this.reservation = reservation;
        this.title = title;
        this.agenda = agenda;
        this.organizerDisplayName = organizerDisplayName;
        this.instructions = instructions;
        this.contactInfo = contactInfo;
        this.visibility = visibility;
        this.createdAt = createdAt;
    }

    public Reservation getReservation() { return reservation; }
    public String getTitle() { return title; }
    public String getAgenda() { return agenda; }
    public String getOrganizerDisplayName() { return organizerDisplayName; }
    public String getInstructions() { return instructions; }
    public String getContactInfo() { return contactInfo; }
    public MeetingVisibility getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
}
