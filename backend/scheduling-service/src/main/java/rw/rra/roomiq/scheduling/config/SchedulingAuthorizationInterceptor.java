package rw.rra.roomiq.scheduling.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.scheduling.integration.SchedulingAuthorizationClient;

@Component
public class SchedulingAuthorizationInterceptor implements HandlerInterceptor {
    private final SchedulingAuthorizationClient authorizationClient;

    public SchedulingAuthorizationInterceptor(SchedulingAuthorizationClient authorizationClient) {
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
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Authentication is required");
        }
        authorizationClient.authorize(authorization,
                HttpMethod.GET.matches(request.getMethod()) ? "READ" : "MANAGE");
        return true;
    }
}