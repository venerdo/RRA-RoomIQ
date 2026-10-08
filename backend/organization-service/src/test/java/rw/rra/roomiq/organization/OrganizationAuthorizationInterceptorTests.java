package rw.rra.roomiq.organization;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.organization.config.OrganizationAuthorizationClient;
import rw.rra.roomiq.organization.config.OrganizationAuthorizationInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrganizationAuthorizationInterceptorTests {
    @Mock
    private OrganizationAuthorizationClient authorizationClient;

    private OrganizationAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new OrganizationAuthorizationInterceptor(authorizationClient);
    }

    @Test
    void readRequestsDelegateReadAuthorization() throws Exception {
        MockHttpServletRequest request = request("GET", "Bearer active-token");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(authorizationClient).authorize("Bearer active-token", "READ");
    }

    @Test
    void mutationRequestsDelegateManageAuthorization() throws Exception {
        MockHttpServletRequest request = request("PUT", "Bearer active-token");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(authorizationClient).authorize("Bearer active-token", "MANAGE");
    }

    @Test
    void missingBearerTokenIsRejectedBeforeCallingIdentity() {
        MockHttpServletRequest request = request("POST", null);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("AUTHENTICATION_REQUIRED");
                });
        verifyNoInteractions(authorizationClient);
    }

    private MockHttpServletRequest request(String method, String bearerToken) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/countries");
        if (bearerToken != null) {
            request.addHeader("Authorization", bearerToken);
        }
        return request;
    }
}