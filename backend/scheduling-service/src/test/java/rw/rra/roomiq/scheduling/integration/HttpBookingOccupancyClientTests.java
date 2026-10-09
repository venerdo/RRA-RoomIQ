package rw.rra.roomiq.scheduling.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpBookingOccupancyClientTests {
    private static final String URL = "http://booking.local";
    private static final String AUTHORIZATION = "Bearer scheduling-caller";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpBookingOccupancyClient client = new HttpBookingOccupancyClient(builder, URL);

    @BeforeEach
    void addCallerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", AUTHORIZATION);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void callsTheVersionedProtectedBookingContractAndDeserializesItsSnapshot() {
        UUID roomId = UUID.randomUUID();
        String snapshot = "2026-10-09T07:00:00Z";
        server.expect(requestTo(URL + "/api/v1/internal/availability/occupancy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", AUTHORIZATION))
                .andExpect(jsonPath("$.contractVersion").value("v1"))
                .andRespond(withSuccess("""
                        {"success":true,"message":"occupancy snapshot",
                         "data":{"snapshotAt":"%s","rooms":[
                           {"roomId":"%s","intervals":[
                             {"startsAt":"2026-10-09T09:00:00Z","endsAt":"2026-10-09T10:05:00Z"}]}]},
                         "metadata":{},"timestamp":"2026-10-09T07:00:00Z"}
                        """.formatted(snapshot, roomId), MediaType.APPLICATION_JSON));

        BookingOccupancyClient.OccupancySnapshot result = client.occupancy(List.of(roomId),
                Instant.parse("2026-10-09T08:00:00Z"), Instant.parse("2026-10-09T12:00:00Z"));

        assertThat(result.snapshotAt()).isEqualTo(Instant.parse(snapshot));
        assertThat(result.rooms()).singleElement().satisfies(room -> {
            assertThat(room.roomId()).isEqualTo(roomId);
            assertThat(room.intervals()).containsExactly(new BookingOccupancyClient.OccupiedInterval(
                    Instant.parse("2026-10-09T09:00:00Z"), Instant.parse("2026-10-09T10:05:00Z")));
        });
        server.verify();
    }

    @Test
    void mapsMissingBookingOccupancyApiToExplicitFailClosedDependencyError() {
        server.expect(requestTo(URL + "/api/v1/internal/availability/occupancy"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.occupancy(List.of(UUID.randomUUID()),
                Instant.parse("2026-10-09T08:00:00Z"), Instant.parse("2026-10-09T12:00:00Z")))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> {
                    DomainException domainException = (DomainException) exception;
                    assertThat(domainException.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(domainException.code()).isEqualTo("BOOKING_OCCUPANCY_UNAVAILABLE");
                    assertThat(domainException).hasMessageContaining("no availability is being claimed");
                });
        server.verify();
    }
}
