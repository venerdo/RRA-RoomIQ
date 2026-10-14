package rw.rra.roomiq.room.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpBookingEventRecipientClientTests {
    private static final String URL = "http://booking.local";
    private static final String TOKEN = "Bearer test-token";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BookingEventRecipientClient client = new HttpBookingEventRecipientClient(builder, URL);

    @BeforeEach
    void setCallerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsCallerTokenAndValidatesVersionRoomAndRecipients() {
        UUID roomId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2030-04-10T10:00:00Z");
        Instant endsAt = Instant.parse("2030-04-10T11:00:00Z");
        server.expect(requestTo(URL + "/api/v1/internal/notifications/room-event-recipients"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", TOKEN))
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.officeBuildingId").value(buildingId.toString()))
                .andExpect(jsonPath("$.startsAt").value(startsAt.toString()))
                .andExpect(jsonPath("$.endsAt").value(endsAt.toString()))
                .andExpect(jsonPath("$.contractVersion").value("v1"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"contractVersion":"v1","roomId":"%s",
                         "recipientUserIds":["%s"]}}
                        """.formatted(roomId, recipientId), MediaType.APPLICATION_JSON));

        assertThat(client.resolveRoomEventRecipients(roomId, buildingId, startsAt, endsAt))
                .containsExactly(recipientId);
        server.verify();
    }

    @Test
    void rejectsMalformedRecipientResponseRatherThanPersistingAnEmptyAudience() {
        UUID roomId = UUID.randomUUID();
        server.expect(requestTo(URL + "/api/v1/internal/notifications/room-event-recipients"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"contractVersion":"v0","roomId":"%s",
                         "recipientUserIds":[]}}
                        """.formatted(roomId), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.resolveRoomEventRecipients(
                roomId, UUID.randomUUID(), Instant.parse("2030-04-10T10:00:00Z"), null))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).status())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }
}
