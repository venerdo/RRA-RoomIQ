package rw.rra.roomiq.booking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.BookingRequestType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_request")
public class BookingRequest extends BookingEntity {
    @Column(name = "request_reference", nullable = false, unique = true, columnDefinition = "varchar")
    private String requestReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, columnDefinition = "varchar")
    private BookingRequestType requestType;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "office_building_id", nullable = false)
    private UUID officeBuildingId;

    @Column(name = "recurrence_rule_id")
    private UUID recurrenceRuleId;

    @Column(nullable = false, columnDefinition = "varchar")
    private String title;

    @Column(columnDefinition = "text")
    private String purpose;

    @Column(name = "requested_start", nullable = false)
    private Instant requestedStart;

    @Column(name = "requested_end", nullable = false)
    private Instant requestedEnd;

    @Column(name = "attendee_count", nullable = false)
    private int attendeeCount;

    @Column(name = "external_guests")
    private Boolean externalGuests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private BookingRequestStatus status;

    @Column(name = "idempotency_key", unique = true, columnDefinition = "varchar")
    private String idempotencyKey;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BookingRequest() {
    }

    public BookingRequest(String requestReference, BookingRequestType requestType, UUID requestedByUserId,
                          UUID departmentId, UUID roomId, UUID officeBuildingId, UUID recurrenceRuleId,
                          String title, String purpose, Instant requestedStart, Instant requestedEnd,
                          int attendeeCount, Boolean externalGuests, BookingRequestStatus status,
                          String idempotencyKey, Instant createdAt) {
        this.requestReference = requestReference;
        this.requestType = requestType;
        this.requestedByUserId = requestedByUserId;
        this.departmentId = departmentId;
        this.roomId = roomId;
        this.officeBuildingId = officeBuildingId;
        this.recurrenceRuleId = recurrenceRuleId;
        this.title = title;
        this.purpose = purpose;
        this.requestedStart = requestedStart;
        this.requestedEnd = requestedEnd;
        this.attendeeCount = attendeeCount;
        this.externalGuests = externalGuests;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public String getRequestReference() { return requestReference; }
    public BookingRequestType getRequestType() { return requestType; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public UUID getDepartmentId() { return departmentId; }
    public UUID getRoomId() { return roomId; }
    public UUID getOfficeBuildingId() { return officeBuildingId; }
    public UUID getRecurrenceRuleId() { return recurrenceRuleId; }
    public String getTitle() { return title; }
    public String getPurpose() { return purpose; }
    public Instant getRequestedStart() { return requestedStart; }
    public Instant getRequestedEnd() { return requestedEnd; }
    public int getAttendeeCount() { return attendeeCount; }
    public Boolean getExternalGuests() { return externalGuests; }
    public BookingRequestStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean submitForApproval() {
        if (status != BookingRequestStatus.DRAFT) {
            return false;
        }
        status = BookingRequestStatus.PENDING_APPROVAL;
        return true;
    }

    public boolean approve() {
        if (status != BookingRequestStatus.PENDING_APPROVAL) {
            return false;
        }
        status = BookingRequestStatus.APPROVED;
        return true;
    }

    public boolean reject() {
        if (status != BookingRequestStatus.PENDING_APPROVAL) {
            return false;
        }
        status = BookingRequestStatus.REJECTED;
        return true;
    }
}
