package rw.rra.roomiq.booking.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookingAuthorizationInterceptorTests {
    private final BookingAuthorizationClient authorizationClient = mock(BookingAuthorizationClient.class);
    private final BookingAuthorizationInterceptor interceptor =
            new BookingAuthorizationInterceptor(authorizationClient);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void authenticatesEachVersionedBookingRequestAndStoresIdentityActor() {
        MockHttpServletRequest request = request("GET", "/api/v1/bookings", "Bearer test-token");
        UUID actorId = UUID.randomUUID();
        when(authorizationClient.authenticate("Bearer test-token")).thenReturn(actorId);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(request.getAttribute(BookingAuthorizationInterceptor.ACTOR_USER_ID_ATTRIBUTE))
                .isEqualTo(actorId);
        verify(authorizationClient).authenticate("Bearer test-token");
    }

    @Test
    void directBookingRouteUsesTheSameVersionedBearerGuard() {
        MockHttpServletRequest request = request("POST", "/api/v1/bookings/direct", "Bearer direct-booking-test");
        UUID actorId = UUID.randomUUID();
        when(authorizationClient.authenticate(anyString())).thenReturn(actorId);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        assertThat(request.getAttribute(BookingAuthorizationInterceptor.ACTOR_USER_ID_ATTRIBUTE))
                .isEqualTo(actorId);
        verify(authorizationClient).authenticate(anyString());
    }

    @Test
    void optionsRequestsAreAuthenticatedAndDelegatedToo() {
        MockHttpServletRequest request = request("OPTIONS", "/api/v1/bookings", "Bearer test-token");
        when(authorizationClient.authenticate("Bearer test-token")).thenReturn(UUID.randomUUID());

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();

        verify(authorizationClient).authenticate("Bearer test-token");
    }

    @Test
    void rejectsMissingOrMalformedBearerTokensBeforeCallingIdentity() {
        for (String token : new String[] {null, "Basic forbidden", "Bearer "}) {
            MockHttpServletRequest request = request("POST", "/api/v1/bookings", token);
            assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(DomainException.class)
                    .hasMessage("Authentication is required");
        }
        verifyNoInteractions(authorizationClient);
    }

    private MockHttpServletRequest request(String method, String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (token != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, token);
        }
        return request;
    }
}
