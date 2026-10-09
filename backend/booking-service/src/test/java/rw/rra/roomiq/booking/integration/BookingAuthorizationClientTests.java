package rw.rra.roomiq.booking.integration;

import jakarta.servlet.http.HttpServletRequest;
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
import rw.rra.roomiq.booking.integration.BookingAuthorizationRequest.Action;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BookingAuthorizationClientTests {
    private static final String URL = "http://identity.local";
    private static final String CALLER_TOKEN = "Bearer opaque-test-token";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BookingAuthorizationClient client = new BookingAuthorizationClient(builder, URL);

    @BeforeEach
    void setCallerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", CALLER_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsTheCallerTokenAndBookingDecisionFactsToIdentity() {
        UUID actorId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        server.expect(requestTo(URL + "/api/v1/internal/authorization/booking"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", CALLER_TOKEN))
                .andExpect(jsonPath("$.action").value("REQUEST_CREATE"))
                .andExpect(jsonPath("$.buildingId").value(buildingId.toString()))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.vipRoom").value(true))
                .andRespond(withSuccess("{\"actorUserId\":\"" + actorId + "\"}", MediaType.APPLICATION_JSON));

        UUID authorizedActor = client.authorizeCurrentCaller(
                new BookingAuthorizationRequest(Action.REQUEST_CREATE, buildingId, departmentId, null, true));

        assertThat(authorizedActor).isEqualTo(actorId);
        server.verify();
    }

    @Test
    void mapsIdentityAuthenticationFailureWithoutProceeding() {
        server.expect(requestTo(URL + "/api/v1/internal/authorization/booking"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.authorizeCurrentCaller(
                new BookingAuthorizationRequest(Action.AUTHENTICATE, null, null, null, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).status())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        server.verify();
    }

    @Test
    void mapsIdentityAuthorizationFailureWithoutProceeding() {
        server.expect(requestTo(URL + "/api/v1/internal/authorization/booking"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.authorizeCurrentCaller(
                new BookingAuthorizationRequest(Action.DIRECT_CREATE, UUID.randomUUID(), null, null, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).status())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        server.verify();
    }

    @Test
    void failsClosedWhenIdentityIsUnavailable() {
        server.expect(requestTo(URL + "/api/v1/internal/authorization/booking"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertUnavailable();
        server.verify();
    }

    @Test
    void failsClosedWhenIdentityResponseHasNoActor() {
        server.expect(requestTo(URL + "/api/v1/internal/authorization/booking"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertUnavailable();
        server.verify();
    }

    @Test
    void refusesMissingOrMalformedCallerTokenWithoutCallingIdentity() {
        HttpServletRequest request = new MockHttpServletRequest();
        ((MockHttpServletRequest) request).addHeader("Authorization", "Basic forbidden");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(() -> client.authorizeCurrentCaller(
                new BookingAuthorizationRequest(Action.AUTHENTICATE, null, null, null, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> assertThat(((DomainException) exception).status())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        server.verify();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> client.authorizeCurrentCaller(
                new BookingAuthorizationRequest(Action.AUTHENTICATE, null, null, null, null)))
                .isInstanceOf(DomainException.class)
                .satisfies(exception -> {
                    DomainException domainException = (DomainException) exception;
                    assertThat(domainException.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(domainException.code()).isEqualTo("IDENTITY_AUTHORIZATION_UNAVAILABLE");
                });
    }
}
