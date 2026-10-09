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
    public static final String ACTOR_USER_ID_ATTRIBUTE = "schedulingAuthenticatedActorUserId";
    private static final String CONSTRAINT_VALIDATION_PATH = "/api/v1/scheduling-constraints/validate";
    private static final String AVAILABILITY_SEARCH_PATH = "/api/v1/availability/search";
    private final SchedulingAuthorizationClient authorizationClient;

    public SchedulingAuthorizationInterceptor(SchedulingAuthorizationClient authorizationClient) {
        this.authorizationClient = authorizationClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Authentication is required");
        }
        boolean readOnlyOperation = HttpMethod.GET.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || (HttpMethod.POST.matches(request.getMethod())
                && (CONSTRAINT_VALIDATION_PATH.equals(
                        request.getRequestURI().substring(request.getContextPath().length()))
                || AVAILABILITY_SEARCH_PATH.equals(
                        request.getRequestURI().substring(request.getContextPath().length()))));
        request.setAttribute(ACTOR_USER_ID_ATTRIBUTE,
                authorizationClient.authorize(authorization, readOnlyOperation ? "READ" : "MANAGE"));
        return true;
    }
}