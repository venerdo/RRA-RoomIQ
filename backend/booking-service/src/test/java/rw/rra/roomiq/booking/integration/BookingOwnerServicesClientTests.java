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
                        {"success":true,"message":"ok","data":{"workingCalendarId":"%s","valid":true}}
                        """.formatted(calendarId), MediaType.APPLICATION_JSON));

        var result = client.validateRequest(facts(), TOKEN, Instant.parse("2026-10-09T12:00:00Z"));

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.departmentId()).isEqualTo(departmentId);
        assertThat(result.officeBuildingId()).isEqualTo(buildingId);
        assertThat(result.vipRoom()).isTrue();
        assertThat(result.timezone()).isEqualTo("Africa/Kigali");
        assertThat(result.approvalRequired()).isTrue();
        assertThat(result.releaseBufferMinutes()).isEqualTo(5);
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

    private BookingRequestFacts facts() {
        return new BookingRequestFacts(departmentId, roomId, buildingId, null,
                startsAt, endsAt, 5, false);
    }
}
