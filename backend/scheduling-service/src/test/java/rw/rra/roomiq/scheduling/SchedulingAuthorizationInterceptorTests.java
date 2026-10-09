package rw.rra.roomiq.scheduling;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.config.SchedulingAuthorizationInterceptor;
import rw.rra.roomiq.scheduling.integration.SchedulingAuthorizationClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulingAuthorizationInterceptorTests {
    private final SchedulingAuthorizationClient authorizationClient = mock(SchedulingAuthorizationClient.class);
    private final SchedulingAuthorizationInterceptor interceptor =
            new SchedulingAuthorizationInterceptor(authorizationClient);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void availabilitySearchUsesReadAuthorization() {
        HttpServletRequest request = request("POST", "/api/v1/availability/search", "Bearer user-token");
        when(authorizationClient.authorize("Bearer user-token", "READ")).thenReturn(UUID.randomUUID());

        interceptor.preHandle(request, response, new Object());

        verify(authorizationClient).authorize("Bearer user-token", "READ");
    }

    @Test
    void availabilitySearchRejectsMissingAuthenticationBeforeDelegation() {
        HttpServletRequest request = request("POST", "/api/v1/availability/search", null);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(DomainException.class)
                .hasMessage("Authentication is required");
    }

    private HttpServletRequest request(String method, String path, String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getContextPath()).thenReturn("");
        when(request.getHeader("Authorization")).thenReturn(authorization);
        return request;
    }
}
