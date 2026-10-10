package rw.rra.roomiq.booking.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.booking.integration.BookingAuthorizationRequest.Action;
import rw.rra.roomiq.booking.integration.BookingAuthorizationResponse.DirectBookingAuthority;
import rw.rra.roomiq.common.web.DomainException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class BookingAuthorizationClient {
    private final RestClient identityClient;

    @Autowired
    public BookingAuthorizationClient(
            @Value("${roomiq.identity.url:http://localhost:8081}") String identityUrl) {
        this(timeoutBoundBuilder(RestClient.builder()), identityUrl);
    }

    BookingAuthorizationClient(RestClient.Builder builder, String identityUrl) {
        this.identityClient = builder.baseUrl(identityUrl).build();
    }

    public UUID authenticate(String bearerToken) {
        return authorize(bearerToken,
                new BookingAuthorizationRequest(Action.AUTHENTICATE, null, null, null, null));
    }

    public UUID authorizeCurrentCaller(BookingAuthorizationRequest request) {
        return authorize(currentAuthorization(), request);
    }

    public UUID authorize(String bearerToken, BookingAuthorizationRequest request) {
        return authorizeResponse(bearerToken, request).actorUserId();
    }

    public BookingAuthorizationResponse authorizeApproval(UUID requesterUserId, UUID buildingId) {
        return authorizeResponse(currentAuthorization(),
                new BookingAuthorizationRequest(Action.APPROVE, buildingId, null, requesterUserId, null));
    }

    public UUID authorizeReservationLifecycle(UUID organizerUserId, UUID departmentId, UUID buildingId) {
        BookingAuthorizationResponse response = authorizeResponse(currentAuthorization(),
                new BookingAuthorizationRequest(Action.RESERVATION_LIFECYCLE, buildingId, departmentId,
                        organizerUserId, null));
        if (!organizerUserId.equals(response.resourceOwnerUserId())) {
            throw unavailable();
        }
        return response.actorUserId();
    }

    public BookingAuthorizationResponse authorizeDirectBooking(UUID buildingId, UUID departmentId,
                                                               boolean vipRoom) {
        BookingAuthorizationResponse response = authorizeResponse(currentAuthorization(),
                new BookingAuthorizationRequest(Action.DIRECT_CREATE, buildingId, departmentId, null, vipRoom));
        if (response.directBookingAuthority() == null
                || !response.actorUserId().equals(response.resourceOwnerUserId())
                || response.resourceOwnerDisplayName() == null
                || response.resourceOwnerDisplayName().isBlank()) {
            throw unavailable();
        }
        return response;
    }

    private BookingAuthorizationResponse authorizeResponse(String bearerToken, BookingAuthorizationRequest request) {
        if (!isBearerToken(bearerToken) || request == null || request.action() == null) {
            throw authenticationRequired();
        }
        try {
            BookingAuthorizationResponse response = identityClient.post()
                    .uri("/api/v1/internal/authorization/booking")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(BookingAuthorizationResponse.class);
            if (response == null || response.actorUserId() == null) {
                throw unavailable();
            }
            return response;
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw authenticationRequired();
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Booking operation is not authorized");
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private static String currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (isBearerToken(authorization)) {
                return authorization;
            }
        }
        throw authenticationRequired();
    }

    private static boolean isBearerToken(String authorization) {
        return authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                && !authorization.substring(7).isBlank();
    }

    private static DomainException authenticationRequired() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required");
    }

    private static DomainException unavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AUTHORIZATION_UNAVAILABLE",
                "Identity authorization is unavailable; the Booking operation cannot proceed");
    }

    private static RestClient.Builder timeoutBoundBuilder(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder.requestFactory(requestFactory);
    }
}
