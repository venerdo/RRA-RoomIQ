package rw.rra.roomiq.organization.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import rw.rra.roomiq.common.web.DomainException;

@Component
public class OrganizationAuthorizationInterceptor implements HandlerInterceptor {
    private final OrganizationAuthorizationClient authorizationClient;

    public OrganizationAuthorizationInterceptor(OrganizationAuthorizationClient authorizationClient) {
        this.authorizationClient = authorizationClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            throw new DomainException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED", "Authentication is required");
        }

        String action = HttpMethod.GET.matches(request.getMethod()) ? "READ" : "MANAGE";
        authorizationClient.authorize(authorization, action);
        return true;
    }
}