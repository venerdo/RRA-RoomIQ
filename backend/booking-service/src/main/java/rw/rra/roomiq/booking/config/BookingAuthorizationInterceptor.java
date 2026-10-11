package rw.rra.roomiq.booking.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class BookingAuthorizationInterceptor implements HandlerInterceptor {
    public static final String ACTOR_USER_ID_ATTRIBUTE = "bookingAuthenticatedActorUserId";
    private static final Pattern PUBLIC_MEETING_CARD_PATH =
            Pattern.compile("/api/v1/public/meetings/[^/]+");
    private final BookingAuthorizationClient authorizationClient;

    public BookingAuthorizationInterceptor(BookingAuthorizationClient authorizationClient) {
        this.authorizationClient = authorizationClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("GET".equals(request.getMethod())
                && PUBLIC_MEETING_CARD_PATH.matcher(request.getServletPath()).matches()) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.substring(7).isBlank()) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Authentication is required");
        }
        UUID actorUserId = authorizationClient.authenticate(authorization);
        request.setAttribute(ACTOR_USER_ID_ATTRIBUTE, actorUserId);
        return true;
    }
}
