package rw.rra.roomiq.room.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class HttpOrganizationDirectoryClientTests {
    private static final String BASE_URL = "http://organization.test";
    private static final String AUTHORIZATION = "Bearer caller-token";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsBearerTokenAndAcceptsActiveFloorInSpecifiedBuilding() {
        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        TestClient fixture = client();
        MockRestServiceServer server = fixture.server();
        setAuthorization();

        server.expect(requestTo(BASE_URL + "/api/v1/office-buildings/" + buildingId))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andRespond(withSuccess(buildingJson(buildingId), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/floors/" + floorId))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andRespond(withSuccess(floorJson(floorId, buildingId), MediaType.APPLICATION_JSON));

        fixture.client().validateBuildingAndFloor(buildingId, floorId);
        server.verify();
    }

    @Test
    void rejectsFloorOwnedByAnotherBuilding() {
        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        TestClient fixture = client();
        MockRestServiceServer server = fixture.server();
        setAuthorization();
        server.expect(requestTo(BASE_URL + "/api/v1/office-buildings/" + buildingId))
                .andRespond(withSuccess(buildingJson(buildingId), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/floors/" + floorId))
                .andRespond(withSuccess(floorJson(floorId, UUID.randomUUID()), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().validateBuildingAndFloor(buildingId, floorId))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not belong");
        server.verify();
    }

    @Test
    void failsClosedWhenOrganizationServiceIsUnavailable() {
        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        TestClient fixture = client();
        MockRestServiceServer server = fixture.server();
        setAuthorization();
        server.expect(requestTo(BASE_URL + "/api/v1/office-buildings/" + buildingId))
                .andRespond(withServerError());

        assertThatThrownBy(() -> fixture.client().validateBuildingAndFloor(buildingId, floorId))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("unavailable");
        server.verify();
    }

        @Test
        void validatesActiveDepartmentAndRejectsOutOfBuildingDepartment() {
        UUID departmentId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        TestClient fixture = client();
        MockRestServiceServer server = fixture.server();
        setAuthorization();
        server.expect(requestTo(BASE_URL + "/api/v1/departments/" + departmentId))
            .andExpect(method(GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
            .andRespond(withSuccess(departmentJson(departmentId, buildingId, "ACTIVE"),
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/departments/" + departmentId))
            .andExpect(method(GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
            .andRespond(withSuccess(departmentJson(departmentId, UUID.randomUUID(), "ACTIVE"),
                MediaType.APPLICATION_JSON));

        fixture.client().validateDepartment(departmentId, buildingId);
        assertThatThrownBy(() -> fixture.client().validateDepartment(departmentId, buildingId))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("outside the room rule building scope");
        server.verify();
        }

        @Test
        void rejectsInactiveDepartment() {
        UUID departmentId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        TestClient fixture = client();
        MockRestServiceServer server = fixture.server();
        setAuthorization();
        server.expect(requestTo(BASE_URL + "/api/v1/departments/" + departmentId))
            .andRespond(withSuccess(departmentJson(departmentId, buildingId, "INACTIVE"),
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().validateDepartment(departmentId, buildingId))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("missing or inactive");
        server.verify();
        }

    private static TestClient client() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestClient(new HttpOrganizationDirectoryClient(builder, BASE_URL), server);
    }

    private static void setAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, AUTHORIZATION);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static String buildingJson(UUID buildingId) {
        return "{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":\"" + buildingId
                + "\",\"active\":true},\"metadata\":{},\"timestamp\":\"2026-10-07T00:00:00Z\"}";
    }

    private static String floorJson(UUID floorId, UUID buildingId) {
        return "{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":\"" + floorId
                + "\",\"officeBuildingId\":\"" + buildingId
                + "\",\"active\":true},\"metadata\":{},\"timestamp\":\"2026-10-07T00:00:00Z\"}";
    }

    private static String departmentJson(UUID departmentId, UUID buildingId, String status) {
        return "{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":\"" + departmentId
                + "\",\"officeBuildingId\":\"" + buildingId + "\",\"status\":\"" + status
                + "\"},\"metadata\":{},\"timestamp\":\"2026-10-07T00:00:00Z\"}";
    }

    private record TestClient(HttpOrganizationDirectoryClient client, MockRestServiceServer server) { }
}