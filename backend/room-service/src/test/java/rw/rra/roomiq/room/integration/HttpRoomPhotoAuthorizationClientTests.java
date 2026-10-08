package rw.rra.roomiq.room.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpRoomPhotoAuthorizationClientTests {
    private static final String BASE_URL = "http://identity.test";
    private static final String TOKEN = "Bearer current-user-token";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000321");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsBearerTokenAndBuildingToIdentityAuthorization() {
        UUID buildingId = UUID.randomUUID();
        TestClient fixture = client();
        setAuthorization(TOKEN);
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/internal/authorization/room-management"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(content().json("{\"officeBuildingId\":\"" + buildingId + "\"}"))
                .andRespond(withSuccess("{\"actorUserId\":\"" + ACTOR_ID + "\"}", MediaType.APPLICATION_JSON));

            org.assertj.core.api.Assertions.assertThat(fixture.client().authorizeRoomManagement(buildingId))
                .isEqualTo(ACTOR_ID);
        fixture.server().verify();
    }

            @Test
            void scopeLessRoomCatalogCheckIsForwardedAndReturnsIdentityActor() {
            TestClient fixture = client();
            setAuthorization(TOKEN);
            fixture.server().expect(requestTo(BASE_URL + "/api/v1/internal/authorization/room-management"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(content().json("{\"officeBuildingId\":null}"))
                .andRespond(withSuccess("{\"actorUserId\":\"" + ACTOR_ID + "\"}", MediaType.APPLICATION_JSON));

            org.assertj.core.api.Assertions.assertThat(fixture.client().authorizeRoomManagement(null))
                .isEqualTo(ACTOR_ID);
            fixture.server().verify();
            }

    @Test
    void rejectsIdentityDenialAndFailsClosedOnIdentityOutage() {
        UUID buildingId = UUID.randomUUID();
        TestClient denied = client();
        setAuthorization(TOKEN);
        denied.server().expect(requestTo(BASE_URL + "/api/v1/internal/authorization/room-management"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> denied.client().authorizeRoomManagement(buildingId))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("permission is required");
        denied.server().verify();

        TestClient unavailable = client();
        setAuthorization(TOKEN);
        unavailable.server().expect(requestTo(BASE_URL + "/api/v1/internal/authorization/room-management"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> unavailable.client().authorizeRoomManagement(buildingId))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("unavailable");
        unavailable.server().verify();
    }

    @Test
    void rejectsMissingBearerTokenBeforeCallingIdentity() {
        TestClient fixture = client();
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(() -> fixture.client().authorizeRoomManagement(UUID.randomUUID()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Authentication is required");
        fixture.server().verify();
    }

    private static TestClient client() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestClient(new HttpRoomPhotoAuthorizationClient(builder, BASE_URL), server);
    }

    private static void setAuthorization(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private record TestClient(HttpRoomPhotoAuthorizationClient client, MockRestServiceServer server) { }
}