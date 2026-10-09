package rw.rra.roomiq.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.domain.dto.AvailabilitySearchRequest;
import rw.rra.roomiq.scheduling.domain.dto.AvailabilitySearchResponse;
import rw.rra.roomiq.scheduling.domain.entity.Holiday;
import rw.rra.roomiq.scheduling.domain.entity.RecurrenceRule;
import rw.rra.roomiq.scheduling.domain.entity.WorkingCalendar;
import rw.rra.roomiq.scheduling.domain.entity.WorkingDayWindow;
import rw.rra.roomiq.scheduling.domain.repository.BlockingClosureInterval;
import rw.rra.roomiq.scheduling.domain.repository.ClosurePeriodRepository;
import rw.rra.roomiq.scheduling.domain.repository.HolidayRepository;
import rw.rra.roomiq.scheduling.domain.repository.RecurrenceRuleRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingCalendarRepository;
import rw.rra.roomiq.scheduling.domain.repository.WorkingDayWindowRepository;
import rw.rra.roomiq.scheduling.domain.service.AvailabilitySearchService;
import rw.rra.roomiq.scheduling.integration.BookingOccupancyClient;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient.MaintenancePeriodReference;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient.RoomFacilityReference;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient.RoomReference;
import rw.rra.roomiq.scheduling.integration.RoomAvailabilityClient.RoomRuleReference;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvailabilitySearchServiceTests {
    private static final UUID CALENDAR_ID = UUID.randomUUID();
    private static final UUID BUILDING_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final WorkingCalendarRepository calendarRepository = mock(WorkingCalendarRepository.class);
    private final WorkingDayWindowRepository windowRepository = mock(WorkingDayWindowRepository.class);
    private final HolidayRepository holidayRepository = mock(HolidayRepository.class);
    private final ClosurePeriodRepository closureRepository = mock(ClosurePeriodRepository.class);
    private final RecurrenceRuleRepository recurrenceRuleRepository = mock(RecurrenceRuleRepository.class);
    private final RoomAvailabilityClient roomClient = mock(RoomAvailabilityClient.class);
    private final BookingOccupancyClient bookingClient = mock(BookingOccupancyClient.class);

    private AvailabilitySearchService service;
    private WorkingCalendar calendar;
    private LocalDate date;
    private ZoneId zone;
    private Instant rangeStart;
    private Instant rangeEnd;

    @BeforeEach
    void setUp() {
        service = new AvailabilitySearchService(calendarRepository, windowRepository, holidayRepository,
                closureRepository, recurrenceRuleRepository, roomClient, bookingClient);
        zone = ZoneId.of("Africa/Kigali");
        date = LocalDate.now(zone).plusDays(3);
        calendar = calendar(zone);
        when(calendarRepository.findById(CALENDAR_ID)).thenReturn(Optional.of(calendar));
        when(windowRepository.findAllByWorkingCalendar_IdOrderByDayOfWeek(CALENDAR_ID))
                .thenReturn(List.of(new WorkingDayWindow(calendar, (short) date.getDayOfWeek().getValue(),
                        LocalTime.of(9, 0), LocalTime.of(17, 0), true)));
        when(holidayRepository.findAllByHolidayDateBetweenAndActiveTrueAndBlocksBookingTrue(any(), any()))
                .thenReturn(List.of());
        when(closureRepository.findBlockingOverlaps(eq(BUILDING_ID), any(), any())).thenReturn(List.of());
        when(roomClient.rooms(BUILDING_ID))
                .thenReturn(List.of(new RoomReference(ROOM_ID, BUILDING_ID, "Room A", "A", 8, "AVAILABLE")));
        when(roomClient.buildingRules(BUILDING_ID)).thenReturn(List.of(roomRule()));
        when(roomClient.roomRules(ROOM_ID)).thenReturn(List.of());
        when(roomClient.maintenancePeriods(ROOM_ID)).thenReturn(List.of());
        when(bookingClient.occupancy(anyList(), any(), any())).thenAnswer(invocation -> {
            List<UUID> roomIds = invocation.getArgument(0);
            return new BookingOccupancyClient.OccupancySnapshot(Instant.now(),
                    roomIds.stream().map(id -> new BookingOccupancyClient.RoomOccupancy(id, List.of())).toList());
        });
        rangeStart = date.atTime(8, 0).atZone(zone).toInstant();
        rangeEnd = date.atTime(18, 0).atZone(zone).toInstant();
    }

    @Test
    void subtractsOccupancyAndPreservesHalfOpenAdjacentBoundaries() {
        when(bookingClient.occupancy(anyList(), any(), any())).thenAnswer(invocation -> {
            List<UUID> roomIds = invocation.getArgument(0);
            return new BookingOccupancyClient.OccupancySnapshot(Instant.now(), roomIds.stream()
                    .map(id -> new BookingOccupancyClient.RoomOccupancy(id, List.of(
                            new BookingOccupancyClient.OccupiedInterval(
                                    date.atTime(11, 0).atZone(zone).toInstant(),
                                    date.atTime(12, 5).atZone(zone).toInstant()))))
                    .toList());
        });

        AvailabilitySearchResponse result = service.search(request(null, null, null));

        assertThat(result.bookingOccupancyIncluded()).isTrue();
        assertThat(result.bookingConfirmationRequired()).isTrue();
        assertThat(result.windows()).extracting(window -> window.startsAt().atZone(zone).toLocalTime())
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(12, 5));
        assertThat(result.windows()).extracting(window -> window.endsAt().atZone(zone).toLocalTime())
                .containsExactly(LocalTime.of(11, 0), LocalTime.of(17, 0));
    }

    @Test
    void subtractsAdjacentMaintenanceAndOccupancyWithoutLosingTheBoundary() {
        when(roomClient.maintenancePeriods(ROOM_ID)).thenReturn(List.of(
                new MaintenancePeriodReference(range(date.atTime(10, 0), date.atTime(11, 0))),
                new MaintenancePeriodReference(range(date.atTime(11, 0), date.atTime(12, 0)))));

        AvailabilitySearchResponse result = service.search(request(null, null, null));

        assertThat(result.windows()).hasSize(2);
        assertThat(result.windows().get(0).startsAt().atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.windows().get(0).endsAt().atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(result.windows().get(1).startsAt().atZone(zone).toLocalTime()).isEqualTo(LocalTime.NOON);
        assertThat(result.windows().get(1).endsAt().atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    void subtractsBlockingClosureIntervalsFromTheCalendarWindow() {
        when(closureRepository.findBlockingOverlaps(eq(BUILDING_ID), any(), any()))
                .thenReturn(List.of(closure(date.atTime(12, 0).atZone(zone).toInstant(),
                        date.atTime(13, 0).atZone(zone).toInstant())));

        AvailabilitySearchResponse result = service.search(request(null, null, null));

        assertThat(result.windows()).hasSize(2);
        assertThat(result.windows().get(0).endsAt().atZone(zone).toLocalTime()).isEqualTo(LocalTime.NOON);
        assertThat(result.windows().get(1).startsAt().atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void omitsEntireDatesBlockedByAnApplicableHoliday() {
        when(holidayRepository.findAllByHolidayDateBetweenAndActiveTrueAndBlocksBookingTrue(any(), any()))
                .thenReturn(List.of(new Holiday(calendar, BUILDING_ID, date, "Holiday", true, true, ACTOR_ID)));

        AvailabilitySearchResponse result = service.search(request(null, null, null));

        assertThat(result.windows()).isEmpty();
        assertThat(result.bookingOccupancyIncluded()).isFalse();
        verify(bookingClient, never()).occupancy(anyList(), any(), any());
    }

    @Test
    void returnsEmptyWindowsWhenNoWorkingWindowApplies() {
        when(windowRepository.findAllByWorkingCalendar_IdOrderByDayOfWeek(CALENDAR_ID)).thenReturn(List.of());

        AvailabilitySearchResponse result = service.search(request(null, null, null));

        assertThat(result.windows()).isEmpty();
        verify(bookingClient, never()).occupancy(anyList(), any(), any());
    }

    @Test
    void usesTheCalendarTimezoneForDayWindowsAndExplicitInstantResults() {
        zone = ZoneId.of("America/New_York");
        date = LocalDate.of(2027, 3, 14);
        calendar = calendar(zone);
        when(calendarRepository.findById(CALENDAR_ID)).thenReturn(Optional.of(calendar));
        when(windowRepository.findAllByWorkingCalendar_IdOrderByDayOfWeek(CALENDAR_ID))
                .thenReturn(List.of(new WorkingDayWindow(calendar, (short) date.getDayOfWeek().getValue(),
                        LocalTime.of(1, 0), LocalTime.of(4, 0), true)));
        rangeStart = date.atStartOfDay(zone).toInstant();
        rangeEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        AvailabilitySearchResponse result = service.search(request(null, null, null));

        assertThat(result.timezone()).isEqualTo("America/New_York");
        assertThat(result.windows()).hasSize(1);
        assertThat(result.windows().getFirst().startsAt()).isEqualTo(Instant.parse("2027-03-14T06:00:00Z"));
        assertThat(result.windows().getFirst().endsAt()).isEqualTo(Instant.parse("2027-03-14T08:00:00Z"));
    }

    @Test
    void failsClosedWhenBookingOccupancyIsNotAuthoritative() {
        when(bookingClient.occupancy(anyList(), any(), any()))
                .thenThrow(new DomainException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "BOOKING_OCCUPANCY_UNAVAILABLE", "Unavailable"));

        assertThatThrownBy(() -> service.search(request(null, null, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> {
                    DomainException domainException = (DomainException) exception;
                    assertThat(domainException.status()).isEqualTo(
                            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(domainException.code()).isEqualTo("BOOKING_OCCUPANCY_UNAVAILABLE");
                });
    }

    @Test
    void rejectsStaleBookingOccupancySnapshots() {
        when(bookingClient.occupancy(anyList(), any(), any())).thenAnswer(invocation -> {
            List<UUID> roomIds = invocation.getArgument(0);
            return new BookingOccupancyClient.OccupancySnapshot(Instant.now().minusSeconds(31),
                    roomIds.stream().map(id -> new BookingOccupancyClient.RoomOccupancy(id, List.of())).toList());
        });

        assertThatThrownBy(() -> service.search(request(null, null, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).code())
                        .isEqualTo("BOOKING_OCCUPANCY_STALE"));
    }

    @Test
    void filtersRoomsByWorkingFacilities() {
        UUID facilityId = UUID.randomUUID();
        when(roomClient.facilities(ROOM_ID))
                .thenReturn(List.of(new RoomFacilityReference(facilityId, "WORKING")));

        AvailabilitySearchResponse result = service.search(request(List.of(facilityId), null, null));

        assertThat(result.windows()).isNotEmpty();
        AvailabilitySearchResponse noMatch = service.search(request(List.of(UUID.randomUUID()), null, null));
        assertThat(noMatch.windows()).isEmpty();
    }

    @Test
    void restrictsRecurringSearchesToPersistedBoundedOccurrenceDates() {
        UUID recurrenceId = UUID.randomUUID();
        RecurrenceRule rule = mock(RecurrenceRule.class);
        when(rule.getRrule()).thenReturn("FREQ=DAILY;COUNT=3");
        when(rule.getStartsOn()).thenReturn(date);
        when(rule.getTimezone()).thenReturn(zone.getId());
        when(recurrenceRuleRepository.findById(recurrenceId)).thenReturn(Optional.of(rule));
        when(windowRepository.findAllByWorkingCalendar_IdOrderByDayOfWeek(CALENDAR_ID)).thenReturn(
                java.util.stream.IntStream.rangeClosed(1, 7)
                        .mapToObj(day -> new WorkingDayWindow(calendar, (short) day,
                                LocalTime.of(9, 0), LocalTime.of(17, 0), true))
                        .toList());
        rangeStart = date.atTime(8, 0).atZone(zone).toInstant();
        rangeEnd = date.plusDays(7).atTime(18, 0).atZone(zone).toInstant();

        AvailabilitySearchResponse result = service.search(request(null, null, recurrenceId));

        assertThat(result.windows()).hasSize(3);
        assertThat(result.windows()).extracting(window -> window.occurrenceDate())
                .containsExactly(date, date.plusDays(1), date.plusDays(2));
        assertThat(result.windows()).allSatisfy(window -> assertThat(window.recurrenceRuleId())
                .isEqualTo(recurrenceId));
    }

    private AvailabilitySearchRequest request(List<UUID> facilityIds, UUID departmentId, UUID recurrenceRuleId) {
        return new AvailabilitySearchRequest(CALENDAR_ID, BUILDING_ID, rangeStart, rangeEnd,
                null, 30, facilityIds, departmentId, recurrenceRuleId);
    }

    private RoomRuleReference roomRule() {
        return new RoomRuleReference(UUID.randomUUID(), null, BUILDING_ID,
                30, 240, null, null, true, false, 5, true, null, List.of());
    }

    private WorkingCalendar calendar(ZoneId timezone) {
        WorkingCalendar value = mock(WorkingCalendar.class);
        when(value.getId()).thenReturn(CALENDAR_ID);
        when(value.getOfficeBuildingId()).thenReturn(BUILDING_ID);
        when(value.getTimezone()).thenReturn(timezone.getId());
        when(value.isActive()).thenReturn(true);
        return value;
    }

    private String range(LocalDateTime start, LocalDateTime end) {
        return "[" + start.atZone(zone).toInstant() + "," + end.atZone(zone).toInstant() + ")";
    }

    private BlockingClosureInterval closure(Instant startsAt, Instant endsAt) {
        return new BlockingClosureInterval() {
            @Override
            public Instant getStartsAt() {
                return startsAt;
            }

            @Override
            public Instant getEndsAt() {
                return endsAt;
            }
        };
    }
}
