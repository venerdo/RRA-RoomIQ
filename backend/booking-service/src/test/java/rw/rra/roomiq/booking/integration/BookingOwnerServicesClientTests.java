package rw.rra.roomiq.booking.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import rw.rra.roomiq.booking.integration.BookingOwnerServicesClient.BookingRequestFacts;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class BookingOwnerServicesClientTests {
    private static final String ORGANIZATION_URL = "http://organization.test";
    private static final String ROOM_URL = "http://room.test";
    private static final String SCHEDULING_URL = "http://scheduling.test";
    private static final String TOKEN = "Bearer identity-token";
    private static final UUID buildingId = UUID.randomUUID();
    private static final UUID departmentId = UUID.randomUUID();
    private static final UUID roomId = UUID.randomUUID();
    private static final UUID calendarId = UUID.randomUUID();
    private static final Instant startsAt = Instant.parse("2026-10-10T10:00:00Z");
    private static final Instant endsAt = Instant.parse("2026-10-10T11:00:00Z");
    private MockRestServiceServer server;
    private BookingOwnerServicesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        client = new BookingOwnerServicesClient(builder, ORGANIZATION_URL, ROOM_URL, SCHEDULING_URL);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void validatesCurrentBuildingDepartmentRoomRulesAndSchedulingWithCallerToken() {
        expectGet(ORGANIZATION_URL, "/api/v1/office-buildings/" + buildingId,
                """
                {"success":true,"message":"ok","data":{"id":"%s","active":true,"timezone":"Africa/Kigali","workingCalendarId":"%s"}}
                """.formatted(buildingId, calendarId));
        expectGet(ORGANIZATION_URL, "/api/v1/departments/" + departmentId,
                """
                {"success":true,"message":"ok","data":{"id":"%s","officeBuildingId":"%s","status":"ACTIVE"}}
                """.formatted(departmentId, buildingId));
        expectGet(ROOM_URL, "/api/v1/rooms/" + roomId,
                """
                {"success":true,"message":"ok","data":{"id":"%s","officeBuildingId":"%s","capacity":12,"roomClass":"VIP","status":"AVAILABLE","deletedAt":null}}
                """.formatted(roomId, buildingId));
        expectGet(ROOM_URL, "/api/v1/rooms/" + roomId + "/rules",
                """
                {"success":true,"message":"ok","data":[{"id":"%s","roomId":"%s","minDurationMinutes":30,"maxDurationMinutes":120,"minAdvanceMinutes":5,"maxAdvanceDays":90,"cancellationDeadlineMinutes":null,"recurringAllowed":true,"externalGuestsAllowed":true,"approvalRequired":true,"outsideHoursAllowed":false,"releaseBufferMinutes":5,"active":true,"effectiveFrom":null,"allowedDepartmentIds":["%s"]}]}
                """.formatted(UUID.randomUUID(), roomId, departmentId));
        expectGet(ROOM_URL, "/api/v1/office-buildings/" + buildingId + "/room-rules",
                """
                {"success":true,"message":"ok","data":[]}
                """);
        server.expect(requestTo(SCHEDULING_URL + "/api/v1/scheduling-constraints/validate"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(content().json("""
                        {"workingCalendarId":"%s","officeBuildingId":"%s","startsAt":"%s","endsAt":"%s","timezone":"Africa/Kigali","recurrenceRuleId":null}
                        """.formatted(calendarId, buildingId, startsAt, endsAt)))
                .andRespond(withSuccess("""
                        {"success":true,"message":"ok","data":{"workingCalendarId":"%s",
                         "recurrenceRuleId":null,"timezone":"Africa/Kigali","valid":true,
                         "occurrencesEvaluated":1,"occurrenceIntervals":[
                          {"occurrenceDate":"2026-10-10","startsAt":"%s","endsAt":"%s"}]}}
                        """.formatted(calendarId, startsAt, endsAt), MediaType.APPLICATION_JSON));
        expectGet(ROOM_URL, "/api/v1/rooms/" + roomId + "/maintenance-periods",
                """
                {"success":true,"message":"ok","data":[]}
                """);

        var result = client.validateRequest(facts(), TOKEN, Instant.parse("2026-10-09T12:00:00Z"));

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.officeBuildingId()).isEqualTo(buildingId);
        assertThat(result.vipRoom()).isTrue();
        assertThat(result.timezone()).isEqualTo("Africa/Kigali");
        assertThat(result.approvalRequired()).isTrue();
        assertThat(result.releaseBufferMinutes()).isEqualTo(5);
        assertThat(result.occurrences()).containsExactly(
                new BookingOwnerServicesClient.SchedulingOccurrence(
                        java.time.LocalDate.parse("2026-10-10"), startsAt, endsAt));
    }

    @Test
    void validatesEachRecurringOccurrenceAgainstItsEffectiveRoomRuleAndTimezone() {
        UUID recurrenceRuleId = UUID.randomUUID();
        Instant secondStart = startsAt.plusSeconds(86_400);
        Instant secondEnd = endsAt.plusSeconds(86_400);
        expectBaseOwnerData("""
                [{"id":"%s","roomId":"%s","officeBuildingId":null,"minDurationMinutes":30,
                  "maxDurationMinutes":120,"minAdvanceMinutes":5,"maxAdvanceDays":90,
                  "cancellationDeadlineMinutes":null,"recurringAllowed":true,
                  "externalGuestsAllowed":true,"approvalRequired":false,"outsideHoursAllowed":false,
                  "releaseBufferMinutes":5,"active":true,"effectiveFrom":null,
                  "allowedDepartmentIds":["%s"]},
                 {"id":"%s","roomId":"%s","officeBuildingId":null,"minDurationMinutes":30,
                  "maxDurationMinutes":120,"minAdvanceMinutes":5,"maxAdvanceDays":90,
                  "cancellationDeadlineMinutes":null,"recurringAllowed":true,
                  "externalGuestsAllowed":true,"approvalRequired":true,"outsideHoursAllowed":false,
                  "releaseBufferMinutes":20,"active":true,"effectiveFrom":"2026-10-11T00:00:00Z",
                  "allowedDepartmentIds":["%s"]}]
                """.formatted(UUID.randomUUID(), roomId, departmentId,
                UUID.randomUUID(), roomId, departmentId));
        expectScheduling(recurrenceRuleId, """
                {"workingCalendarId":"%s","recurrenceRuleId":"%s","timezone":"Africa/Kigali","valid":true,
                 "occurrencesEvaluated":2,"violations":[],"occurrenceIntervals":[
                  {"occurrenceDate":"2026-10-10","startsAt":"%s","endsAt":"%s"},
                  {"occurrenceDate":"2026-10-11","startsAt":"%s","endsAt":"%s"}]}
                """.formatted(calendarId, recurrenceRuleId, startsAt, endsAt, secondStart, secondEnd));
        expectGet(ROOM_URL, "/api/v1/rooms/" + roomId + "/maintenance-periods",
                """
                {"success":true,"message":"ok","data":[]}
                """);

        var result = client.validateRequest(new BookingRequestFacts(departmentId, roomId, buildingId,
                recurrenceRuleId, startsAt, endsAt, 5, false), TOKEN,
                Instant.parse("2026-10-09T12:00:00Z"));

        assertThat(result.occurrences()).hasSize(2);
        assertThat(result.approvalRequired()).isTrue();
        assertThat(result.releaseBufferMinutes()).isEqualTo(20);
    }

    @Test
    void rejectsMaintenanceThatOverlapsAnyOccurrenceIncludingItsReleaseBuffer() {
        UUID recurrenceRuleId = UUID.randomUUID();
        Instant secondStart = startsAt.plusSeconds(86_400);
        Instant secondEnd = endsAt.plusSeconds(86_400);
        expectBaseOwnerData("""
                [{"id":"%s","roomId":"%s","officeBuildingId":null,"minDurationMinutes":30,
                  "maxDurationMinutes":120,"minAdvanceMinutes":5,"maxAdvanceDays":90,
                  "cancellationDeadlineMinutes":null,"recurringAllowed":true,
                  "externalGuestsAllowed":true,"approvalRequired":false,"outsideHoursAllowed":false,
                  "releaseBufferMinutes":5,"active":true,"effectiveFrom":null,
                  "allowedDepartmentIds":["%s"]}]
                """.formatted(UUID.randomUUID(), roomId, departmentId));
        expectScheduling(recurrenceRuleId, """
                {"workingCalendarId":"%s","recurrenceRuleId":"%s","timezone":"Africa/Kigali","valid":true,
                 "occurrencesEvaluated":2,"violations":[],"occurrenceIntervals":[
                  {"occurrenceDate":"2026-10-10","startsAt":"%s","endsAt":"%s"},
                  {"occurrenceDate":"2026-10-11","startsAt":"%s","endsAt":"%s"}]}
                """.formatted(calendarId, recurrenceRuleId, startsAt, endsAt, secondStart, secondEnd));
        expectGet(ROOM_URL, "/api/v1/rooms/" + roomId + "/maintenance-periods",
                """
                {"success":true,"message":"ok","data":[{"id":"%s","roomId":"%s",
                 "period":"[2026-10-11T10:30:00Z,2026-10-11T10:45:00Z)","reason":null,
                 "createdByUserId":null}]}
                """.formatted(UUID.randomUUID(), roomId));

        assertThatThrownBy(() -> client.validateRequest(new BookingRequestFacts(departmentId, roomId, buildingId,
                recurrenceRuleId, startsAt, endsAt, 5, false), TOKEN,
                Instant.parse("2026-10-09T12:00:00Z")))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> {
                    DomainException domainException = (DomainException) exception;
                    assertThat(domainException.code()).isEqualTo("ROOM_MAINTENANCE_CONFLICT");
                    assertThat(domainException.getMessage()).contains("2026-10-11");
                });
    }

    @Test
    void returnsSchedulingViolationsScopedToEachOccurrence() {
        expectBaseOwnerData("""
                [{"id":"%s","roomId":"%s","officeBuildingId":null,"minDurationMinutes":30,
                  "maxDurationMinutes":120,"minAdvanceMinutes":5,"maxAdvanceDays":90,
                  "cancellationDeadlineMinutes":null,"recurringAllowed":true,
                  "externalGuestsAllowed":true,"approvalRequired":false,"outsideHoursAllowed":false,
                  "releaseBufferMinutes":5,"active":true,"effectiveFrom":null,
                  "allowedDepartmentIds":["%s"]}]
                """.formatted(UUID.randomUUID(), roomId, departmentId));
        expectScheduling(null, """
                {"workingCalendarId":"%s","recurrenceRuleId":null,"timezone":"Africa/Kigali","valid":false,
                 "occurrencesEvaluated":2,"violations":[
                  {"occurrenceDate":"2026-10-10","code":"BLOCKING_HOLIDAY","message":"Holiday"},
                  {"occurrenceDate":"2026-10-11","code":"BLOCKING_CLOSURE","message":"Closure"}],
                 "occurrenceIntervals":[]}
                """.formatted(calendarId));

        assertThatThrownBy(() -> client.validateRequest(facts(), TOKEN,
                Instant.parse("2026-10-09T12:00:00Z")))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> {
                    DomainException domainException = (DomainException) exception;
                    assertThat(domainException.code()).isEqualTo("SCHEDULING_CONSTRAINTS_INVALID");
                    assertThat(domainException.getMessage())
                            .contains("2026-10-10:BLOCKING_HOLIDAY", "2026-10-11:BLOCKING_CLOSURE");
                });
    }

    @Test
    void rejectsMaintenanceDataForAnotherRoom() {
        expectBaseOwnerData("""
                [{"id":"%s","roomId":"%s","officeBuildingId":null,"minDurationMinutes":30,
                  "maxDurationMinutes":120,"minAdvanceMinutes":5,"maxAdvanceDays":90,
                  "cancellationDeadlineMinutes":null,"recurringAllowed":true,
                  "externalGuestsAllowed":true,"approvalRequired":false,"outsideHoursAllowed":false,
                  "releaseBufferMinutes":5,"active":true,"effectiveFrom":null,
                  "allowedDepartmentIds":["%s"]}]
                """.formatted(UUID.randomUUID(), roomId, departmentId));
        expectScheduling(null, """
                {"workingCalendarId":"%s","recurrenceRuleId":null,"timezone":"Africa/Kigali","valid":true,
                 "occurrencesEvaluated":1,"violations":[],"occurrenceIntervals":[
                  {"occurrenceDate":"2026-10-10","startsAt":"%s","endsAt":"%s"}]}
                """.formatted(calendarId, startsAt, endsAt));
        expectGet(ROOM_URL, "/api/v1/rooms/" + roomId + "/maintenance-periods",
                """
                {"success":true,"message":"ok","data":[{"id":"%s","roomId":"%s",
                 "period":"[2026-10-11T10:30:00Z,2026-10-11T10:45:00Z)","reason":null,
                 "createdByUserId":null}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> client.validateRequest(facts(), TOKEN,
                Instant.parse("2026-10-09T12:00:00Z")))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).code())
                        .isEqualTo("ROOM_SERVICE_UNAVAILABLE"));
    }

    @Test
    void ownerServiceOutageFailsClosed() {
        server.expect(requestTo(ORGANIZATION_URL + "/api/v1/office-buildings/" + buildingId))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.validateRequest(facts(), TOKEN, Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot proceed");
    }

    @Test
    void rejectsMissingCallerTokenBeforeOwnerCalls() {
        assertThatThrownBy(() -> client.validateRequest(facts(), null, Instant.now()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Authentication is required");
    }

    private void expectGet(String baseUrl, String path, String response) {
        server.expect(requestTo(baseUrl + path))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void expectBaseOwnerData(String roomRules) {
        expectGet(ORGANIZATION_URL, "/api/v1/office-buildings/" + buildingId,
                """
                {"success":true,"message":"ok","data":{"id":"%s","active":true,
                 "timezone":"Africa/Kigali","workingCalendarId":"%s"}}
                """.formatted(buildingId, calendarId));
        expectGet(ORGANIZATION_URL, "/api/v1/departments/" + departmentId,
                """
                {"success":true,"message":"ok","data":{"id":"%s","officeBuildingId":"%s","status":"ACTIVE"}}
                """.formatted(departmentId, buildingId));
        expectGet(ROOM_URL, "/api/v1/rooms/" + roomId,
                """
                {"success":true,"message":"ok","data":{"id":"%s","officeBuildingId":"%s","capacity":12,
                 "roomClass":"STANDARD","status":"AVAILABLE","deletedAt":null}}
                """.formatted(roomId, buildingId));
        expectGet(ROOM_URL, "/api/v1/rooms/" + roomId + "/rules",
                """
                {"success":true,"message":"ok","data":%s}
                """.formatted(roomRules));
        expectGet(ROOM_URL, "/api/v1/office-buildings/" + buildingId + "/room-rules",
                """
                {"success":true,"message":"ok","data":[]}
                """);
    }

    private void expectScheduling(UUID recurrenceRuleId, String response) {
        server.expect(requestTo(SCHEDULING_URL + "/api/v1/scheduling-constraints/validate"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(content().json("""
                        {"workingCalendarId":"%s","officeBuildingId":"%s","startsAt":"%s",
                         "endsAt":"%s","timezone":"Africa/Kigali","recurrenceRuleId":%s}
                        """.formatted(calendarId, buildingId, startsAt, endsAt,
                        recurrenceRuleId == null ? "null" : "\"" + recurrenceRuleId + "\"")))
                .andRespond(withSuccess("""
                        {"success":true,"message":"ok","data":%s}
                        """.formatted(response), MediaType.APPLICATION_JSON));
    }

    private BookingRequestFacts facts() {
        return new BookingRequestFacts(departmentId, roomId, buildingId, null,
                startsAt, endsAt, 5, false);
    }
}
