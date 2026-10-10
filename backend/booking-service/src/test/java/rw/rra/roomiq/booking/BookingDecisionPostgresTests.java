package rw.rra.roomiq.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.postgresql.util.PGobject;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import rw.rra.roomiq.booking.domain.dto.BookingDecisionRequest;
import rw.rra.roomiq.booking.domain.entity.Reservation;
import rw.rra.roomiq.booking.domain.entity.BookingRequest;
import rw.rra.roomiq.booking.domain.entity.ReservationOccurrence;
import rw.rra.roomiq.booking.domain.enums.ApprovalDecisionType;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.BookingRequestType;
import rw.rra.roomiq.booking.domain.enums.InviteStatus;
import rw.rra.roomiq.booking.domain.enums.MeetingVisibility;
import rw.rra.roomiq.booking.domain.enums.ParticipantRole;
import rw.rra.roomiq.booking.domain.enums.ReservationStatus;
import rw.rra.roomiq.booking.domain.repository.ApprovalDecisionRepository;
import rw.rra.roomiq.booking.domain.repository.BookingRequestRepository;
import rw.rra.roomiq.booking.domain.repository.MeetingParticipantRepository;
import rw.rra.roomiq.booking.domain.repository.MeetingRepository;
import rw.rra.roomiq.booking.domain.repository.ReservationRepository;
import rw.rra.roomiq.booking.domain.repository.ReservationOccurrenceRepository;
import rw.rra.roomiq.booking.domain.service.BookingDecisionService;
import rw.rra.roomiq.booking.domain.service.ReservationLifecycleService;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.booking.integration.BookingAuthorizationResponse;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient.BookingRequestFacts;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient.SchedulingOccurrence;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient.ValidatedBookingReferences;
import rw.rra.roomiq.common.web.DomainException;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Import(BookingDecisionPostgresTests.TestClients.class)
class BookingDecisionPostgresTests {
    private static final String TOKEN = "Bearer approval-test-token";
    private static final Instant START = Instant.parse("2030-04-10T10:00:00Z");
    private static final Instant END = Instant.parse("2030-04-10T11:00:00Z");
    private static final String FAILING_PARTICIPANT_TRIGGER = "trg_booking_test_fail_participant";
    private static final String FAILING_PARTICIPANT_FUNCTION = "booking_test_fail_participant";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private BookingDecisionService decisionService;
    @Autowired
    private ReservationLifecycleService reservationLifecycleService;
    @Autowired
    private BookingRequestRepository bookingRequests;
    @Autowired
    private ApprovalDecisionRepository decisions;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private ReservationOccurrenceRepository reservationOccurrences;
    @Autowired
    private MeetingRepository meetings;
    @Autowired
    private MeetingParticipantRepository participants;
    @Autowired
    private BookingAuthorizationClient authorizationClient;
    @Autowired
    private BookingOwnerServicesClient ownerServicesClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        reset(authorizationClient, ownerServicesClient);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContextAndFailureTrigger() {
        RequestContextHolder.resetRequestAttributes();
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + FAILING_PARTICIPANT_TRIGGER + " ON meeting_participant");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + FAILING_PARTICIPANT_FUNCTION + "()");
    }

    @Test
    void approvalCommitsDecisionRequestReservationMeetingAndRequesterOrganizerTogether() {
        BookingRequest request = pendingRequest(UUID.randomUUID(), UUID.randomUUID(), START, END);
        configureAuthorization(request, UUID.randomUUID());
        configureOwnerValidation(request, 15);

        var response = decisionService.decide(request.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, "Approved by admin"));

        assertThat(bookingRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingRequestStatus.APPROVED);
        var decision = decisions.findAll().stream()
                .filter(item -> item.getBookingRequest().getId().equals(request.getId())).findFirst().orElseThrow();
        assertThat(decision.getDecision()).isEqualTo(ApprovalDecisionType.APPROVED);
        assertThat(decision.getDecidedByUserId()).isNotEqualTo(request.getRequestedByUserId());
        assertThat(decision.getDecidedAt()).isNotNull();
        assertThat(decision.getComment()).isEqualTo("Approved by admin");

        Reservation reservation = reservations.findAll().stream()
                .filter(item -> item.getBookingRequest().getId().equals(request.getId())).findFirst().orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getOrganizerUserId()).isEqualTo(request.getRequestedByUserId());
        assertThat(reservation.getReleaseBufferMinutes()).isEqualTo(15);
        long occupiedUntilEpoch = jdbcTemplate.queryForObject("""
                SELECT EXTRACT(EPOCH FROM upper(occupied_period))::bigint
                FROM reservation WHERE id = ?
                """, Long.class, reservation.getId());
        assertThat(Instant.ofEpochSecond(occupiedUntilEpoch)).isEqualTo(END.plusSeconds(900));
        List<ReservationOccurrence> persistedOccurrences =
                reservationOccurrences.findAllByReservation_IdOrderByStartAt(reservation.getId());
        assertThat(persistedOccurrences).hasSize(1);
        assertThat(persistedOccurrences.getFirst().getStartAt()).isEqualTo(START);
        assertThat(persistedOccurrences.getFirst().getEndAt()).isEqualTo(END);
        Long occurrenceEnd = jdbcTemplate.queryForObject("""
                SELECT EXTRACT(EPOCH FROM upper(occupied_period))::bigint
                FROM reservation_occurrence WHERE id = ?
                """, Long.class, persistedOccurrences.getFirst().getId());
        assertThat(Instant.ofEpochSecond(occurrenceEnd)).isEqualTo(END.plusSeconds(900));

        var meeting = meetings.findAll().stream()
                .filter(item -> item.getReservation().getId().equals(reservation.getId())).findFirst().orElseThrow();
        assertThat(meeting.getTitle()).isEqualTo(request.getTitle());
        assertThat(meeting.getAgenda()).isEqualTo(request.getPurpose());
        assertThat(meeting.getOrganizerDisplayName()).isEqualTo("Trusted requester");
        assertThat(meeting.getVisibility()).isEqualTo(MeetingVisibility.PRIVATE);
        var organizer = participants.findAll().stream()
                .filter(item -> item.getMeeting().getId().equals(meeting.getId())).findFirst().orElseThrow();
        assertThat(organizer.getUserId()).isEqualTo(request.getRequestedByUserId());
        assertThat(organizer.getDisplayName()).isEqualTo("Trusted requester");
        assertThat(organizer.getRole()).isEqualTo(ParticipantRole.ORGANIZER);
        assertThat(organizer.getInviteStatus()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(response.reservationId()).isEqualTo(reservation.getId());
        assertThat(response.meetingId()).isEqualTo(meeting.getId());
    }

    @Test
    void checkInAndCompletionSetIndependentServerTimestampsAtomically() throws SQLException {
        Reservation reservation = confirmedReservation(UUID.randomUUID(), UUID.randomUUID(), START, END);
        UUID actor = UUID.randomUUID();
        when(authorizationClient.authorizeReservationLifecycle(
                reservation.getOrganizerUserId(), reservation.getBookingRequest().getDepartmentId(),
                reservation.getBookingRequest().getOfficeBuildingId())).thenReturn(actor);

        var checkedIn = reservationLifecycleService.checkIn(reservation.getId());

        assertThat(checkedIn.status()).isEqualTo(ReservationStatus.IN_PROGRESS);
        assertThat(checkedIn.checkedInAt()).isNotNull();
        assertThat(checkedIn.completedAt()).isNull();
        assertThat(reservations.findById(reservation.getId()).orElseThrow().getCheckedInAt())
                .isEqualTo(checkedIn.checkedInAt());
        verify(authorizationClient).authorizeReservationLifecycle(reservation.getOrganizerUserId(),
                reservation.getBookingRequest().getDepartmentId(),
                reservation.getBookingRequest().getOfficeBuildingId());

        var completed = reservationLifecycleService.complete(reservation.getId());

        assertThat(completed.status()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(completed.checkedInAt()).isEqualTo(checkedIn.checkedInAt());
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.completedAt().compareTo(checkedIn.checkedInAt())).isGreaterThanOrEqualTo(0);
        Reservation persisted = reservations.findById(reservation.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(persisted.getCheckedInAt()).isEqualTo(checkedIn.checkedInAt());
        assertThat(persisted.getCompletedAt()).isEqualTo(completed.completedAt());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT (status = 'COMPLETED') = (completed_at IS NOT NULL)
                FROM reservation WHERE id = ?
                """, Boolean.class, reservation.getId())).isTrue();
    }

    @Test
    void invalidAndDuplicateLifecycleTransitionsLeavePersistedTimestampsUnchanged() throws SQLException {
        Reservation reservation = confirmedReservation(UUID.randomUUID(), UUID.randomUUID(), START, END);
        when(authorizationClient.authorizeReservationLifecycle(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(UUID.randomUUID());

        assertLifecycleConflict(() -> reservationLifecycleService.complete(reservation.getId()));
        assertThat(reservations.findById(reservation.getId()).orElseThrow().getCompletedAt()).isNull();

        var checkedIn = reservationLifecycleService.checkIn(reservation.getId());
        assertLifecycleConflict(() -> reservationLifecycleService.checkIn(reservation.getId()));
        assertThat(reservations.findById(reservation.getId()).orElseThrow().getCheckedInAt())
                .isEqualTo(checkedIn.checkedInAt());

        var completed = reservationLifecycleService.complete(reservation.getId());
        assertLifecycleConflict(() -> reservationLifecycleService.complete(reservation.getId()));
        assertThat(reservations.findById(reservation.getId()).orElseThrow().getCompletedAt())
                .isEqualTo(completed.completedAt());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE reservation SET status = 'COMPLETED', completed_at = NULL WHERE id = ?",
                reservation.getId())).isInstanceOf(RuntimeException.class);
        assertThat(reservations.findById(reservation.getId()).orElseThrow().getCompletedAt())
                .isEqualTo(completed.completedAt());
    }

    @Test
    void identityAuthorizationFailureDoesNotChangeReservationLifecycle() throws SQLException {
        Reservation reservation = confirmedReservation(UUID.randomUUID(), UUID.randomUUID(), START, END);
        when(authorizationClient.authorizeReservationLifecycle(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Denied"));

        assertThatThrownBy(() -> reservationLifecycleService.checkIn(reservation.getId()))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).status()).isEqualTo(HttpStatus.FORBIDDEN));
        when(authorizationClient.authorizeReservationLifecycle(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(UUID.randomUUID());
        var checkedIn = reservationLifecycleService.checkIn(reservation.getId());
        reset(authorizationClient);
        when(authorizationClient.authorizeReservationLifecycle(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Denied"));
        assertThatThrownBy(() -> reservationLifecycleService.complete(reservation.getId()))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).status()).isEqualTo(HttpStatus.FORBIDDEN));
        Reservation unchanged = reservations.findById(reservation.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ReservationStatus.IN_PROGRESS);
        assertThat(unchanged.getCheckedInAt()).isEqualTo(checkedIn.checkedInAt());
        assertThat(unchanged.getCompletedAt()).isNull();
    }

    @Test
    void concurrentDuplicateCheckInsCommitOnlyOneLifecycleTransition() throws Exception {
        Reservation reservation = confirmedReservation(UUID.randomUUID(), UUID.randomUUID(), START, END);
        when(authorizationClient.authorizeReservationLifecycle(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> UUID.randomUUID());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> checkIn = () -> {
            MockHttpServletRequest httpRequest = new MockHttpServletRequest();
            httpRequest.addHeader("Authorization", TOKEN);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                reservationLifecycleService.checkIn(reservation.getId());
                return "checked-in";
            } catch (DomainException exception) {
                return exception.code();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        };
        try {
            Future<String> first = executor.submit(checkIn);
            Future<String> second = executor.submit(checkIn);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("checked-in", "RESERVATION_STATE_CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        Reservation persisted = reservations.findById(reservation.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ReservationStatus.IN_PROGRESS);
        assertThat(persisted.getCheckedInAt()).isNotNull();
        assertThat(persisted.getCompletedAt()).isNull();
    }

    @Test
    void completedReservationStillOccupiesItsBufferedHalfOpenInterval() throws SQLException {
        UUID roomId = UUID.randomUUID();
        Reservation reservation = confirmedReservation(UUID.randomUUID(), roomId, START, END);
        when(authorizationClient.authorizeReservationLifecycle(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(UUID.randomUUID());
        reservationLifecycleService.checkIn(reservation.getId());
        reservationLifecycleService.complete(reservation.getId());

        BookingRequest overlapping = pendingRequest(UUID.randomUUID(), roomId, START.plusSeconds(30), END);
        configureAuthorization(overlapping, UUID.randomUUID());
        configureOwnerValidation(overlapping, 5);

        assertThatThrownBy(() -> decisionService.decide(overlapping.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).code())
                        .isEqualTo("ROOM_OCCUPANCY_CONFLICT"));
        assertThat(reservationOccurrences.existsRoomOccupancyConflict(
                roomId, START, END.plusSeconds(300))).isTrue();
    }

    @Test
    void terminalReservationsCannotReenterTheLifecycle() throws SQLException {
        when(authorizationClient.authorizeReservationLifecycle(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(UUID.randomUUID());
        for (ReservationStatus terminalStatus : List.of(ReservationStatus.CANCELLED, ReservationStatus.RELEASED)) {
            Reservation reservation = confirmedReservation(UUID.randomUUID(), UUID.randomUUID(), START, END);
            jdbcTemplate.update("UPDATE reservation SET status = ? WHERE id = ?",
                    terminalStatus.name(), reservation.getId());

            assertLifecycleConflict(() -> reservationLifecycleService.checkIn(reservation.getId()));
            assertLifecycleConflict(() -> reservationLifecycleService.complete(reservation.getId()));
            Reservation unchanged = reservations.findById(reservation.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo(terminalStatus);
            assertThat(unchanged.getCheckedInAt()).isNull();
            assertThat(unchanged.getCompletedAt()).isNull();
        }
    }

    @Test
    void extensionShapedOverlapsAreRejectedByBothDatabaseExclusionBarriers() throws SQLException {
        UUID roomId = UUID.randomUUID();
        Reservation first = confirmedReservation(UUID.randomUUID(), roomId, START, END);
        Instant secondStart = END.plusSeconds(3_600);
        Reservation second = confirmedReservation(UUID.randomUUID(), roomId, secondStart,
                secondStart.plusSeconds(3_600));
        Instant overlappingEnd = second.getEndAt().plusSeconds(300);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE reservation
                SET occupied_period = tstzrange(?::timestamptz, ?::timestamptz, '[)')
                WHERE id = ?
                """, Timestamp.from(first.getStartAt()), Timestamp.from(overlappingEnd), first.getId()))
                .satisfies(error -> assertExclusionViolation(error, "ex_reservation_room_occupied_period"));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE reservation_occurrence
                SET occupied_period = tstzrange(?::timestamptz, ?::timestamptz, '[)')
                WHERE reservation_id = ?
                """, Timestamp.from(first.getStartAt()), Timestamp.from(overlappingEnd), first.getId()))
                .satisfies(error -> assertExclusionViolation(
                        error, "ex_reservation_occurrence_room_occupied_period"));
        Long reservationEnd = jdbcTemplate.queryForObject("""
                SELECT EXTRACT(EPOCH FROM upper(occupied_period))::bigint
                FROM reservation WHERE id = ?
                """, Long.class, first.getId());
        Long occurrenceEnd = jdbcTemplate.queryForObject("""
                SELECT EXTRACT(EPOCH FROM upper(occupied_period))::bigint
                FROM reservation_occurrence WHERE reservation_id = ?
                """, Long.class, first.getId());
        assertThat(Instant.ofEpochSecond(reservationEnd)).isEqualTo(END.plusSeconds(300));
        assertThat(Instant.ofEpochSecond(occurrenceEnd)).isEqualTo(END.plusSeconds(300));
        assertThat(second.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void adjacentBufferedReservationRangesRemainValid() throws SQLException {
        UUID roomId = UUID.randomUUID();
        Reservation first = confirmedReservation(UUID.randomUUID(), roomId, START, END);
        Instant secondStart = END.plusSeconds(300);

        Reservation adjacent = confirmedReservation(UUID.randomUUID(), roomId, secondStart,
                secondStart.plusSeconds(3_600));

        assertThat(first.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(adjacent.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservationOccurrences.findAllByReservation_IdOrderByStartAt(adjacent.getId()))
                .singleElement()
                .satisfies(occurrence -> assertThat(occurrence.getStartAt()).isEqualTo(secondStart));
    }

    @Test
    void rejectionCommitsOnlyTheDecisionAndRejectedRequest() {
        BookingRequest request = pendingRequest(UUID.randomUUID(), UUID.randomUUID(), START, END);
        configureAuthorization(request, UUID.randomUUID());

        var response = decisionService.decide(request.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.REJECTED, null));

        assertThat(response.requestStatus()).isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(bookingRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(decisions.existsByBookingRequest_Id(request.getId())).isTrue();
        assertThat(reservations.existsByBookingRequest_Id(request.getId())).isFalse();
        assertThat(reservationOccurrences.findAllByBookingRequestId(request.getId())).isEmpty();
        assertThat(meetingsForRequest(request.getId())).isZero();
        assertThat(participantsForRequest(request.getId())).isZero();
        assertThat(reservationOccurrences.findAllByBookingRequestId(request.getId())).isEmpty();
    }

    @Test
    void databaseFailureAfterReservationAndMeetingWritesRollsBackEveryApprovalRecord() {
        BookingRequest request = pendingRequest(UUID.randomUUID(), UUID.randomUUID(), START, END);
        configureAuthorization(request, UUID.randomUUID());
        configureOwnerValidation(request, 5);
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION booking_test_fail_participant() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION USING ERRCODE = '23514', CONSTRAINT = 'test_participant_failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_booking_test_fail_participant
                BEFORE INSERT ON meeting_participant
                FOR EACH ROW EXECUTE FUNCTION booking_test_fail_participant()
                """);

        assertThatThrownBy(() -> decisionService.decide(request.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, "Must roll back")))
                .isInstanceOf(RuntimeException.class);

        assertThat(bookingRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingRequestStatus.PENDING_APPROVAL);
        assertThat(decisions.existsByBookingRequest_Id(request.getId())).isFalse();
        assertThat(reservations.existsByBookingRequest_Id(request.getId())).isFalse();
        assertThat(meetingsForRequest(request.getId())).isZero();
        assertThat(participantsForRequest(request.getId())).isZero();
    }

    @Test
    void staleAndAlreadyDecidedRequestsCannotOverwriteDecisionHistory() {
        BookingRequest request = pendingRequest(UUID.randomUUID(), UUID.randomUUID(), START, END);
        configureAuthorization(request, UUID.randomUUID());
        decisionService.decide(request.getId(), new BookingDecisionRequest(ApprovalDecisionType.REJECTED, null));
        reset(authorizationClient, ownerServicesClient);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("Authorization", TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));
        when(authorizationClient.authorizeApproval(request.getRequestedByUserId(), request.getOfficeBuildingId()))
                .thenReturn(new BookingAuthorizationResponse(UUID.randomUUID(),
                        request.getRequestedByUserId(), "Trusted requester"));

        assertThatThrownBy(() -> decisionService.decide(request.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, "Late competing decision")))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    DomainException domainException = (DomainException) error;
                    assertThat(domainException.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(domainException.code()).isEqualTo("BOOKING_REQUEST_STATE_CONFLICT");
                });

        assertThat(decisionsForRequest(request.getId())).isEqualTo(1);
        assertThat(reservationsForRequest(request.getId())).isZero();
        assertThat(bookingRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingRequestStatus.REJECTED);
    }

    @Test
    void identityAuthorizationFailureLeavesPendingRequestUntouched() {
        BookingRequest request = pendingRequest(UUID.randomUUID(), UUID.randomUUID(), START, END);
        when(authorizationClient.authorizeApproval(request.getRequestedByUserId(), request.getOfficeBuildingId()))
                .thenThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Denied"));

        assertThatThrownBy(() -> decisionService.decide(request.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).status()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(decisionsForRequest(request.getId())).isZero();
        assertThat(reservationsForRequest(request.getId())).isZero();
        assertThat(bookingRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingRequestStatus.PENDING_APPROVAL);
    }

    @Test
    void concurrentCompetingDecisionsOnlyCommitOneDecision() throws Exception {
        BookingRequest request = pendingRequest(UUID.randomUUID(), UUID.randomUUID(), START, END);
        configureAuthorization(request, UUID.randomUUID());
        configureOwnerValidation(request, 5);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> decide = () -> {
            MockHttpServletRequest httpRequest = new MockHttpServletRequest();
            httpRequest.addHeader("Authorization", TOKEN);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                decisionService.decide(request.getId(),
                        new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null));
                return "approved";
            } catch (DomainException exception) {
                return exception.code();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        };
        try {
            Future<String> first = executor.submit(decide);
            Future<String> second = executor.submit(decide);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("approved", "BOOKING_REQUEST_STATE_CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        assertThat(decisionsForRequest(request.getId())).isEqualTo(1);
        assertThat(reservationsForRequest(request.getId())).isEqualTo(1);
    }

    @Test
    void concurrentRoomConfirmationsCannotBothOccupyTheSameInterval() throws Exception {
        UUID sharedRoomId = UUID.randomUUID();
        BookingRequest firstRequest = pendingRequest(UUID.randomUUID(), sharedRoomId, START, END);
        BookingRequest secondRequest = pendingRequest(UUID.randomUUID(), sharedRoomId, START, END);
        CyclicBarrier ownerValidationBarrier = new CyclicBarrier(2);
        when(authorizationClient.authorizeApproval(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> new BookingAuthorizationResponse(
                        UUID.randomUUID(), invocation.getArgument(0), "Trusted requester"));
        doAnswer(invocation -> {
            BookingRequestFacts facts = invocation.getArgument(0);
            ownerValidationBarrier.await(10, TimeUnit.SECONDS);
            return references(facts, 5);
        }).when(ownerServicesClient).validateRequest(any(BookingRequestFacts.class), eq(TOKEN), any(Instant.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> approveRequest(decisionService, firstRequest, ready, start));
            Future<String> second = executor.submit(() -> approveRequest(decisionService, secondRequest, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("approved", "ROOM_OCCUPANCY_CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        assertThat(decisionsForRequest(firstRequest.getId())
                + decisionsForRequest(secondRequest.getId())).isEqualTo(1);
        assertThat(reservationsForRequest(firstRequest.getId())
                + reservationsForRequest(secondRequest.getId())).isEqualTo(1);
        assertThat(occurrencesForRequest(firstRequest.getId())
                + occurrencesForRequest(secondRequest.getId())).isEqualTo(1);
        assertThat(List.of(bookingRequests.findById(firstRequest.getId()).orElseThrow().getStatus(),
                bookingRequests.findById(secondRequest.getId()).orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(BookingRequestStatus.APPROVED, BookingRequestStatus.PENDING_APPROVAL);
    }

    @Test
    void concurrentRecurringConfirmationsCannotPartiallyOccupyTheSameSeriesDates() throws Exception {
        UUID sharedRoomId = UUID.randomUUID();
        BookingRequest firstRequest = pendingRecurringRequest(UUID.randomUUID(), sharedRoomId,
                UUID.randomUUID(), START, END);
        BookingRequest secondRequest = pendingRecurringRequest(UUID.randomUUID(), sharedRoomId,
                UUID.randomUUID(), START, END);
        CyclicBarrier ownerValidationBarrier = new CyclicBarrier(2);
        when(authorizationClient.authorizeApproval(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> new BookingAuthorizationResponse(
                        UUID.randomUUID(), invocation.getArgument(0), "Trusted requester"));
        doAnswer(invocation -> {
            BookingRequestFacts facts = invocation.getArgument(0);
            ownerValidationBarrier.await(10, TimeUnit.SECONDS);
            Instant nextStart = facts.startsAt().plusSeconds(86_400);
            return recurrenceReferences(facts, List.of(
                    new SchedulingOccurrence(LocalDate.parse("2030-04-10"), facts.startsAt(), facts.endsAt()),
                    new SchedulingOccurrence(LocalDate.parse("2030-04-11"), nextStart,
                            facts.endsAt().plusSeconds(86_400))));
        }).when(ownerServicesClient).validateRequest(any(BookingRequestFacts.class), eq(TOKEN), any(Instant.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> approveRequest(decisionService, firstRequest, ready, start));
            Future<String> second = executor.submit(() -> approveRequest(decisionService, secondRequest, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("approved", "ROOM_OCCUPANCY_CONFLICT");
        } finally {
            executor.shutdownNow();
        }

        assertThat(reservationsForRequest(firstRequest.getId())
                + reservationsForRequest(secondRequest.getId())).isEqualTo(1);
        assertThat(occurrencesForRequest(firstRequest.getId())
                + occurrencesForRequest(secondRequest.getId())).isEqualTo(2);
        assertThat(decisionsForRequest(firstRequest.getId())
                + decisionsForRequest(secondRequest.getId())).isEqualTo(1);
        assertThat(List.of(bookingRequests.findById(firstRequest.getId()).orElseThrow().getStatus(),
                bookingRequests.findById(secondRequest.getId()).orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(BookingRequestStatus.APPROVED, BookingRequestStatus.PENDING_APPROVAL);
    }

    @Test
    void laterRecurrenceOccurrenceConflictIsRejectedWithoutPartialApproval() {
        UUID sharedRoomId = UUID.randomUUID();
        Instant secondDayStart = START.plusSeconds(86_400);
        Instant thirdDayStart = START.plusSeconds(172_800);
        BookingRequest firstRequest = pendingRecurringRequest(UUID.randomUUID(), sharedRoomId,
                UUID.randomUUID(), START, END);
        BookingRequest secondRequest = pendingRecurringRequest(UUID.randomUUID(), sharedRoomId,
                UUID.randomUUID(), secondDayStart, secondDayStart.plusSeconds(3_600));
        configureAuthorization(firstRequest, UUID.randomUUID());
        configureAuthorization(secondRequest, UUID.randomUUID());
        when(ownerServicesClient.validateRequest(any(BookingRequestFacts.class), eq(TOKEN), any(Instant.class)))
                .thenAnswer(invocation -> {
                    BookingRequestFacts facts = invocation.getArgument(0);
                    Instant nextStart = facts.startsAt().plusSeconds(86_400);
                    Instant nextEnd = facts.endsAt().plusSeconds(86_400);
                    return recurrenceReferences(facts, List.of(
                            new SchedulingOccurrence(
                                    facts.startsAt().atZone(ZoneId.of("Africa/Kigali")).toLocalDate(),
                                    facts.startsAt(), facts.endsAt()),
                            new SchedulingOccurrence(
                                    nextStart.atZone(ZoneId.of("Africa/Kigali")).toLocalDate(),
                                    nextStart, nextEnd)));
                });

        decisionService.decide(firstRequest.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null));
        assertThat(occurrencesForRequest(firstRequest.getId())).isEqualTo(2);

        assertThatThrownBy(() -> decisionService.decide(secondRequest.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> {
                    DomainException domainException = (DomainException) exception;
                    assertThat(domainException.code()).isEqualTo("ROOM_OCCUPANCY_CONFLICT");
                    assertThat(domainException.getMessage()).contains("2030-04-11");
                });

        assertThat(bookingRequests.findById(secondRequest.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingRequestStatus.PENDING_APPROVAL);
        assertThat(decisionsForRequest(secondRequest.getId())).isZero();
        assertThat(reservationsForRequest(secondRequest.getId())).isZero();
        assertThat(occurrencesForRequest(secondRequest.getId())).isZero();
    }

    @Test
    void recurringApprovalPersistsTheWholeSeriesAndMeetingAtomically() {
        BookingRequest request = pendingRecurringRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), START, END);
        configureAuthorization(request, UUID.randomUUID());
        Instant secondStart = START.plusSeconds(86_400);
        when(ownerServicesClient.validateRequest(any(BookingRequestFacts.class), eq(TOKEN), any(Instant.class)))
                .thenAnswer(invocation -> {
                    BookingRequestFacts facts = invocation.getArgument(0);
                    return recurrenceReferences(facts, List.of(
                            new SchedulingOccurrence(LocalDate.parse("2030-04-10"), facts.startsAt(), facts.endsAt()),
                            new SchedulingOccurrence(LocalDate.parse("2030-04-11"), secondStart,
                                    secondStart.plusSeconds(3_600))));
                });

        decisionService.decide(request.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, "series approved"));

        assertThat(bookingRequests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(decisionsForRequest(request.getId())).isEqualTo(1);
        assertThat(reservationsForRequest(request.getId())).isEqualTo(1);
        assertThat(meetingsForRequest(request.getId())).isEqualTo(1);
        assertThat(participantsForRequest(request.getId())).isEqualTo(1);
        assertThat(reservationOccurrences.findAllByBookingRequestId(request.getId()))
                .extracting(ReservationOccurrence::getStartAt)
                .containsExactly(START, secondStart);
    }

    @Test
    void aNewReservationMayStartExactlyAtThePriorReleaseBufferBoundary() {
        UUID sharedRoomId = UUID.randomUUID();
        BookingRequest firstRequest = pendingRequest(UUID.randomUUID(), sharedRoomId, START, END);
        Instant secondStart = END.plusSeconds(15 * 60L);
        BookingRequest secondRequest = pendingRequest(UUID.randomUUID(), sharedRoomId,
                secondStart, secondStart.plusSeconds(3_600));
        configureAuthorization(firstRequest, UUID.randomUUID());
        configureAuthorization(secondRequest, UUID.randomUUID());
        configureOwnerValidation(firstRequest, 15);
        configureOwnerValidation(secondRequest, 15);

        decisionService.decide(firstRequest.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null));
        decisionService.decide(secondRequest.getId(),
                new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null));

        assertThat(reservationsForRequest(firstRequest.getId())).isEqualTo(1);
        assertThat(reservationsForRequest(secondRequest.getId())).isEqualTo(1);
        assertThat(occurrencesForRequest(firstRequest.getId())).isEqualTo(1);
        assertThat(occurrencesForRequest(secondRequest.getId())).isEqualTo(1);
    }

    private String approveRequest(BookingDecisionService service, BookingRequest request,
                                 CountDownLatch ready, CountDownLatch start) throws Exception {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("Authorization", TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        try {
            service.decide(request.getId(), new BookingDecisionRequest(ApprovalDecisionType.APPROVED, null));
            return "approved";
        } catch (DomainException exception) {
            return exception.code();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private BookingRequest pendingRequest(UUID requesterId, UUID roomId, Instant startsAt, Instant endsAt) {
        BookingRequest request = new BookingRequest("BR-" + UUID.randomUUID(), BookingRequestType.SECRETARY_REQUEST,
                requesterId, UUID.randomUUID(), roomId, UUID.randomUUID(), null, "Test meeting", "Test agenda",
                startsAt, endsAt, 3, false, BookingRequestStatus.PENDING_APPROVAL, null, Instant.now());
        return bookingRequests.saveAndFlush(request);
    }

    private BookingRequest pendingRecurringRequest(UUID requesterId, UUID roomId, UUID recurrenceRuleId,
                                                   Instant startsAt, Instant endsAt) {
        BookingRequest request = new BookingRequest("BR-" + UUID.randomUUID(),
                BookingRequestType.SECRETARY_REQUEST, requesterId, UUID.randomUUID(), roomId, UUID.randomUUID(),
                recurrenceRuleId, "Test meeting", "Test agenda", startsAt, endsAt, 3, false,
                BookingRequestStatus.PENDING_APPROVAL, null, Instant.now());
        return bookingRequests.saveAndFlush(request);
    }

    private Reservation confirmedReservation(UUID organizerId, UUID roomId, Instant startsAt, Instant endsAt)
            throws SQLException {
        BookingRequest request = pendingRequest(organizerId, roomId, startsAt, endsAt);
        request.approve();
        bookingRequests.saveAndFlush(request);
        PGobject occupied = new PGobject();
        occupied.setType("tstzrange");
        occupied.setValue("[" + startsAt + "," + endsAt.plusSeconds(300) + ")");
        Reservation reservation = reservations.saveAndFlush(new Reservation(request, roomId, organizerId, null, occupied,
                startsAt, endsAt, 5, ReservationStatus.CONFIRMED, Instant.now()));
        reservationOccurrences.saveAndFlush(new ReservationOccurrence(reservation, roomId, startsAt, endsAt, occupied));
        return reservation;
    }

    private static void assertLifecycleConflict(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    DomainException exception = (DomainException) error;
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("RESERVATION_STATE_CONFLICT");
                });
    }

    private static void assertExclusionViolation(Throwable failure, String constraintName) {
        SQLException postgresException = null;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                postgresException = sqlException;
                break;
            }
        }
        assertThat(postgresException != null).isTrue();
        assertThat(postgresException.getSQLState()).isEqualTo("23P01");
        assertThat(postgresException.getMessage()).contains(constraintName);
    }

    private void configureAuthorization(BookingRequest request, UUID actorId) {
        when(authorizationClient.authorizeApproval(request.getRequestedByUserId(), request.getOfficeBuildingId()))
                .thenReturn(new BookingAuthorizationResponse(actorId,
                        request.getRequestedByUserId(), "Trusted requester"));
    }

    private void configureOwnerValidation(BookingRequest request, int releaseBufferMinutes) {
        when(ownerServicesClient.validateRequest(any(BookingRequestFacts.class), eq(TOKEN), any(Instant.class)))
                .thenAnswer(invocation -> references(invocation.getArgument(0), releaseBufferMinutes));
    }

    private ValidatedBookingReferences references(BookingRequestFacts facts, int releaseBufferMinutes) {
        LocalDate occurrenceDate = facts.startsAt().atZone(ZoneId.of("Africa/Kigali")).toLocalDate();
        return recurrenceReferences(facts, List.of(
                new SchedulingOccurrence(occurrenceDate, facts.startsAt(), facts.endsAt())), releaseBufferMinutes);
    }

    private ValidatedBookingReferences recurrenceReferences(BookingRequestFacts facts,
                                                             List<SchedulingOccurrence> intervals) {
        return recurrenceReferences(facts, intervals, 5);
    }

    private ValidatedBookingReferences recurrenceReferences(BookingRequestFacts facts,
                                                             List<SchedulingOccurrence> intervals,
                                                             int releaseBufferMinutes) {
        return new ValidatedBookingReferences(facts.officeBuildingId(), facts.departmentId(), facts.roomId(),
                false, "Africa/Kigali", true, releaseBufferMinutes, intervals);
    }

    private long decisionsForRequest(UUID requestId) {
        return decisions.findAll().stream()
                .filter(decision -> decision.getBookingRequest().getId().equals(requestId))
                .count();
    }

    private long reservationsForRequest(UUID requestId) {
        return reservations.findAll().stream()
                .filter(reservation -> reservation.getBookingRequest().getId().equals(requestId))
                .count();
    }

    private long meetingsForRequest(UUID requestId) {
        return meetings.findAll().stream()
                .filter(meeting -> meeting.getReservation().getBookingRequest().getId().equals(requestId))
                .count();
    }

    private long participantsForRequest(UUID requestId) {
        return participants.findAll().stream()
                .filter(participant -> participant.getMeeting().getReservation().getBookingRequest().getId()
                        .equals(requestId))
                .count();
    }

    private long occurrencesForRequest(UUID requestId) {
        return reservationOccurrences.findAllByBookingRequestId(requestId).size();
    }

    @TestConfiguration
    static class TestClients {
        @Bean
        @Primary
        BookingAuthorizationClient testBookingAuthorizationClient() {
            return org.mockito.Mockito.mock(BookingAuthorizationClient.class);
        }

        @Bean
        @Primary
        BookingOwnerServicesClient testBookingOwnerServicesClient() {
            return org.mockito.Mockito.mock(BookingOwnerServicesClient.class);
        }
    }
}
