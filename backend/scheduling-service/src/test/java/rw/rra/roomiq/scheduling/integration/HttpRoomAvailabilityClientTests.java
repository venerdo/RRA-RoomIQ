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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRoomAvailabilityClientTests {
    private static final String URL = "http://room.local";
    private static final String AUTHORIZATION = "Bearer scheduling-caller";
    private static final UUID BUILDING_ID = UUID.randomUUID();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpRoomAvailabilityClient client = new HttpRoomAvailabilityClient(builder, URL);

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
    void readsCurrentRoomInventoryThroughTheOwningServiceAndForwardsAuthorization() {
        UUID roomId = UUID.randomUUID();
        server.expect(requestTo(URL + "/api/v1/rooms?officeBuildingId=" + BUILDING_ID
                        + "&status=AVAILABLE&page=0&size=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", AUTHORIZATION))
                .andRespond(withSuccess("""
                        {"success":true,"message":"Rooms retrieved",
                         "data":{"content":[
                           {"id":"%s","floorId":"%s","officeBuildingId":"%s","roomTypeId":"%s",
                            "name":"Board Room","code":"BR-1","description":null,"capacity":12,
                            "roomClass":"MEETING_ROOM","status":"AVAILABLE","version":0,
                            "createdAt":"2026-10-09T07:00:00Z","deletedAt":null}],
                           "page":0,"size":100,"totalElements":1,"totalPages":1,"sortBy":"name",
                           "sortDirection":"ASC"},
                         "metadata":{},"timestamp":"2026-10-09T07:00:00Z"}
                        """.formatted(roomId, UUID.randomUUID(), BUILDING_ID, UUID.randomUUID()),
                        MediaType.APPLICATION_JSON));

        var rooms = client.rooms(BUILDING_ID);

        assertThat(rooms).singleElement().satisfies(room -> {
            assertThat(room.id()).isEqualTo(roomId);
            assertThat(room.officeBuildingId()).isEqualTo(BUILDING_ID);
            assertThat(room.capacity()).isEqualTo(12);
            assertThat(room.status()).isEqualTo("AVAILABLE");
        });
        server.verify();
    }

    @Test
    void failsClosedWhenTheRoomOwnerServiceIsUnavailable() {
        server.expect(requestTo(URL + "/api/v1/rooms?officeBuildingId=" + BUILDING_ID
                        + "&status=AVAILABLE&page=0&size=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.rooms(BUILDING_ID))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).code())
                        .isEqualTo("ROOM_AVAILABILITY_UNAVAILABLE"));
        server.verify();
    }
}
