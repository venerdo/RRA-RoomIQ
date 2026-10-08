package rw.rra.roomiq.room.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;

@Component
public class HttpRoomPhotoAuthorizationClient implements RoomPhotoAuthorizationClient {
    private final RestClient restClient;

    @Autowired
    public HttpRoomPhotoAuthorizationClient(
            @Value("${identity.service.url:http://localhost:8081}") String baseUrl) {
        this(RestClient.builder(), baseUrl);
    }

    HttpRoomPhotoAuthorizationClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
        public UUID authorizeRoomManagement(UUID officeBuildingId) {
        String authorization = currentAuthorization();
        try {
            RoomManagementAuthorizationResponse response = restClient.post()
                    .uri("/api/v1/internal/authorization/room-management")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(new RoomManagementRequest(officeBuildingId))
                    .retrieve()
                .body(RoomManagementAuthorizationResponse.class);
            if (response == null || response.actorUserId() == null) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_SERVICE_UNAVAILABLE",
                        "Room authorization returned no actor identity");
            }
            return response.actorUserId();
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Room management requires authentication");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Room management permission is required");
        } catch (RestClientException exception) {
            throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_SERVICE_UNAVAILABLE",
                    "Room photo authorization is unavailable");
        }
    }

    private static String currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                    && !authorization.substring(7).isBlank()) {
                return authorization;
            }
        }
        throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
            "Authentication is required to manage Room resources");
    }

    private record RoomManagementRequest(UUID officeBuildingId) { }

    private record RoomManagementAuthorizationResponse(UUID actorUserId) { }
}