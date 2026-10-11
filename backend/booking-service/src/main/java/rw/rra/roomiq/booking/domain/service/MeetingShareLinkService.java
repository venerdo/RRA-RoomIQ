package rw.rra.roomiq.booking.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.booking.domain.dto.CreateMeetingShareLinkRequest;
import rw.rra.roomiq.booking.domain.dto.MeetingShareLinkCreatedResponse;
import rw.rra.roomiq.booking.domain.dto.MeetingShareLinkResponse;
import rw.rra.roomiq.booking.domain.dto.PublicMeetingCardResponse;
import rw.rra.roomiq.booking.domain.entity.Meeting;
import rw.rra.roomiq.booking.domain.entity.MeetingShareLink;
import rw.rra.roomiq.booking.domain.entity.Reservation;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.ReservationStatus;
import rw.rra.roomiq.booking.domain.repository.MeetingRepository;
import rw.rra.roomiq.booking.domain.repository.MeetingShareLinkRepository;
import rw.rra.roomiq.booking.domain.repository.ReservationRepository;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient;
import rw.rra.roomiq.booking.integration.MeetingAuthorizationRequest;
import rw.rra.roomiq.booking.integration.MeetingAuthorizationResponse;
import rw.rra.roomiq.common.web.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static rw.rra.roomiq.booking.integration.MeetingAuthorizationRequest.Action.MANAGE;

@Service
public class MeetingShareLinkService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final MeetingRepository meetings;
    private final ReservationRepository reservations;
    private final MeetingShareLinkRepository shareLinks;
    private final BookingAuthorizationClient authorization;
    private final BookingOwnerServicesClient ownerServices;
    private final Clock clock;

    @Autowired
    public MeetingShareLinkService(MeetingRepository meetings, ReservationRepository reservations,
                                   MeetingShareLinkRepository shareLinks,
                                   BookingAuthorizationClient authorization,
                                   BookingOwnerServicesClient ownerServices) {
        this(meetings, reservations, shareLinks, authorization, ownerServices, Clock.systemUTC());
    }

    MeetingShareLinkService(MeetingRepository meetings, ReservationRepository reservations,
                            MeetingShareLinkRepository shareLinks,
                            BookingAuthorizationClient authorization,
                            BookingOwnerServicesClient ownerServices, Clock clock) {
        this.meetings = meetings;
        this.reservations = reservations;
        this.shareLinks = shareLinks;
        this.authorization = authorization;
        this.ownerServices = ownerServices;
        this.clock = clock;
    }

    @Transactional
    public MeetingShareLinkCreatedResponse create(UUID meetingId, CreateMeetingShareLinkRequest request) {
        if (meetingId == null || request == null) {
            throw invalidRequest();
        }
        Meeting initial = meetings.findById(meetingId).orElseThrow(MeetingShareLinkService::meetingNotFound);
        UUID reservationId = initial.getReservation().getId();
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(MeetingShareLinkService::meetingNotFound);
        Meeting meeting = meetings.findByIdForUpdate(meetingId)
                .orElseThrow(MeetingShareLinkService::meetingNotFound);
        ensureConsistentFacts(meeting, reservation);
        MeetingAuthorizationResponse identity = authorizeManage(meeting);
        ensureEligibleForCreation(meeting, reservation);
        if (!meeting.updateOrganizerDisplayName(identity.organizerDisplayName())) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AUTHORIZATION_UNAVAILABLE",
                    "Identity did not return the trusted organizer display name");
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = request.expiresAt() == null ? null
                : request.expiresAt().truncatedTo(ChronoUnit.MICROS);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "SHARE_LINK_EXPIRY_INVALID",
                    "Share-link expiry must be in the future");
        }
        String timezone = ownerServices.authoritativeBuildingTimezone(
                reservation.getBookingRequest().getOfficeBuildingId(), authorization.currentBearerToken());
        String rawToken = createToken();
        MeetingShareLink link = shareLinks.saveAndFlush(new MeetingShareLink(
                meeting, hashToken(rawToken), timezone, identity.actorUserId(), expiresAt, null, 0));
        return new MeetingShareLinkCreatedResponse(link.getId(), rawToken, expiresAt);
    }

    @Transactional
    public MeetingShareLinkResponse revoke(UUID shareLinkId) {
        if (shareLinkId == null) {
            throw invalidRequest();
        }
        MeetingShareLink initial = shareLinks.findById(shareLinkId)
                .orElseThrow(MeetingShareLinkService::shareLinkNotFound);
        UUID meetingId = initial.getMeeting().getId();
        UUID reservationId = initial.getMeeting().getReservation().getId();
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(MeetingShareLinkService::shareLinkNotFound);
        Meeting meeting = meetings.findByIdForUpdate(meetingId)
                .orElseThrow(MeetingShareLinkService::shareLinkNotFound);
        MeetingShareLink link = shareLinks.findByIdForUpdate(shareLinkId)
                .orElseThrow(MeetingShareLinkService::shareLinkNotFound);
        ensureConsistentFacts(meeting, reservation);
        if (!meetingId.equals(link.getMeeting().getId())) {
            throw inconsistentFacts();
        }
        authorizeManage(meeting);
        if (link.getRevokedAt() == null) {
            link.revoke(clock.instant().truncatedTo(ChronoUnit.MICROS));
        }
        return response(link);
    }

    @Transactional
    public PublicMeetingCardResponse readPublicCard(String token) {
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            throw publicCardNotFound();
        }
        MeetingShareLink link = shareLinks.findByTokenHashForUpdate(hashToken(token))
                .orElseThrow(MeetingShareLinkService::publicCardNotFound);
        Instant now = clock.instant();
        if (link.getRevokedAt() != null || link.getExpiresAt() != null && !link.getExpiresAt().isAfter(now)) {
            throw publicCardNotFound();
        }
        Meeting meeting = link.getMeeting();
        Reservation reservation = meeting.getReservation();
        if (reservation.getBookingRequest().getStatus() != BookingRequestStatus.APPROVED
                || reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.RELEASED
                || meeting.getTitle() == null || meeting.getTitle().isBlank()
                || meeting.getOrganizerDisplayName() == null || meeting.getOrganizerDisplayName().isBlank()) {
            throw publicCardNotFound();
        }
        if (!link.recordView()) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "SHARE_LINK_VIEW_COUNT_EXHAUSTED",
                    "Share-link view counter is exhausted");
        }
        try {
            ZoneId zone = ZoneId.of(link.getTimezone());
            return new PublicMeetingCardResponse(meeting.getTitle(),
                    reservation.getStartAt().atZone(zone).toOffsetDateTime(),
                    reservation.getEndAt().atZone(zone).toOffsetDateTime(),
                    link.getTimezone(), meeting.getOrganizerDisplayName());
        } catch (DateTimeException exception) {
            throw publicCardNotFound();
        }
    }

    private MeetingAuthorizationResponse authorizeManage(Meeting meeting) {
        Reservation reservation = meeting.getReservation();
        var request = reservation.getBookingRequest();
        MeetingAuthorizationResponse response = authorization.authorizeMeeting(new MeetingAuthorizationRequest(
                MANAGE, request.getOfficeBuildingId(), request.getDepartmentId(),
                reservation.getOrganizerUserId(), meeting.getVisibility(), null));
        if (response.actorUserId() == null || !Objects.equals(response.organizerUserId(),
                reservation.getOrganizerUserId()) || response.organizerDisplayName() == null
                || response.organizerDisplayName().isBlank()) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AUTHORIZATION_UNAVAILABLE",
                    "Identity returned inconsistent meeting authorization facts");
        }
        return response;
    }

    private static void ensureEligibleForCreation(Meeting meeting, Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.CONFIRMED
                || reservation.getBookingRequest().getStatus() != BookingRequestStatus.APPROVED) {
            throw new DomainException(HttpStatus.CONFLICT, "SHARE_LINK_MEETING_NOT_ELIGIBLE",
                    "A share link can be created only for an approved booking with a confirmed reservation");
        }
    }

    private static void ensureConsistentFacts(Meeting meeting, Reservation reservation) {
        var request = reservation.getBookingRequest();
        if (!Objects.equals(meeting.getReservation().getId(), reservation.getId())
                || !Objects.equals(reservation.getOrganizerUserId(), request.getRequestedByUserId())
                || !Objects.equals(reservation.getRoomId(), request.getRoomId())
                || request.getOfficeBuildingId() == null || request.getDepartmentId() == null
                || request.getStatus() == null || meeting.getVisibility() == null) {
            throw inconsistentFacts();
        }
    }

    private static MeetingShareLinkResponse response(MeetingShareLink link) {
        return new MeetingShareLinkResponse(link.getId(), link.getExpiresAt(), link.getRevokedAt(),
                link.getViewCount(), link.getTimezone());
    }

    private static String createToken() {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static DomainException invalidRequest() {
        return new DomainException(HttpStatus.BAD_REQUEST, "SHARE_LINK_REQUEST_INVALID",
                "Share-link request is invalid");
    }

    private static DomainException meetingNotFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND", "Meeting was not found");
    }

    private static DomainException shareLinkNotFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "SHARE_LINK_NOT_FOUND",
                "Meeting share link was not found");
    }

    private static DomainException publicCardNotFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "PUBLIC_MEETING_NOT_FOUND",
                "Public meeting card was not found");
    }

    private static DomainException inconsistentFacts() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "MEETING_FACTS_INCONSISTENT",
                "Persisted meeting ownership or scope is inconsistent");
    }
}
