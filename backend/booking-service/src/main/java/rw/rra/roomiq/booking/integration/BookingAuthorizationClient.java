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

    public UUID authorizeRoomManagement(UUID officeBuildingId) {
        String bearerToken = currentAuthorization();
        if (officeBuildingId == null) {
            throw unavailable();
        }
        try {
            RoomManagementAuthorizationResponse response = identityClient.post()
                    .uri("/api/v1/internal/authorization/room-management")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RoomManagementAuthorizationRequest(officeBuildingId))
                    .retrieve()
                    .body(RoomManagementAuthorizationResponse.class);
            if (response == null || response.actorUserId() == null) {
                throw unavailable();
            }
            return response.actorUserId();
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw authenticationRequired();
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Room event recipient lookup is not authorized");
        } catch (RestClientException exception) {
            throw unavailable();
        }
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

    public BookingAuthorizationResponse authorizeCancellation(UUID resourceOwnerUserId, UUID buildingId) {
        BookingAuthorizationResponse response = authorizeResponse(currentAuthorization(),
                new BookingAuthorizationRequest(Action.CANCEL, buildingId, null, resourceOwnerUserId, null));
        if (!resourceOwnerUserId.equals(response.resourceOwnerUserId())
                || response.cancellationAuthority() == null) {
            throw unavailable();
        }
        return response;
    }

    public String currentBearerToken() {
        return currentAuthorization();
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

    public MeetingAuthorizationResponse authorizeMeeting(MeetingAuthorizationRequest request) {
        String bearerToken = currentAuthorization();
        if (request == null || request.action() == null || request.buildingId() == null
                || request.departmentId() == null || request.organizerUserId() == null
                || request.visibility() == null
                || request.action() == MeetingAuthorizationRequest.Action.READ
                && request.participantUserId() != null) {
            throw unavailable();
        }
        try {
            MeetingAuthorizationResponse response = identityClient.post()
                    .uri("/api/v1/internal/authorization/meeting")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MeetingAuthorizationResponse.class);
            if (response == null || response.actorUserId() == null
                    || !request.buildingId().equals(response.buildingId())
                    || !request.departmentId().equals(response.departmentId())
                    || !request.organizerUserId().equals(response.organizerUserId())
                    || request.visibility() != response.visibility()
                    || !java.util.Objects.equals(request.participantUserId(), response.participantUserId())
                    || request.participantUserId() != null
                    && (response.participantDisplayName() == null
                    || response.participantDisplayName().isBlank())) {
                throw unavailable();
            }
            return response;
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw authenticationRequired();
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "MEETING_ACCESS_DENIED",
                    "Meeting access is not authorized");
        } catch (RestClientException exception) {
            throw unavailable();
        }
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

    private record RoomManagementAuthorizationRequest(UUID officeBuildingId) {
    }

    private record RoomManagementAuthorizationResponse(UUID actorUserId) {
    }

    private static RestClient.Builder timeoutBoundBuilder(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return builder.requestFactory(requestFactory);
    }
}
