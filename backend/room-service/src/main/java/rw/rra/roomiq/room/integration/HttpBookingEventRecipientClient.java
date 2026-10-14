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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import rw.rra.roomiq.common.web.DomainException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class HttpBookingEventRecipientClient implements BookingEventRecipientClient {
    private final RestClient restClient;

    @Autowired
    public HttpBookingEventRecipientClient(
            @Value("${booking.service.url:http://localhost:8085}") String baseUrl) {
        this(timeoutBoundBuilder(), baseUrl);
    }

    HttpBookingEventRecipientClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public List<UUID> resolveRoomEventRecipients(UUID roomId, UUID officeBuildingId,
                                                 Instant startsAt, Instant endsAt) {
        if (roomId == null || officeBuildingId == null || startsAt == null
                || endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Room event recipient lookup requires valid room and time bounds");
        }
        String authorization = currentAuthorization();
        try {
            ApiResponse response = restClient.post()
                    .uri("/api/v1/internal/notifications/room-event-recipients")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(new Request(roomId, officeBuildingId, startsAt, endsAt, "v1"))
                    .retrieve()
                    .body(ApiResponse.class);
            if (response == null || !response.success() || response.data() == null
                    || !"v1".equals(response.data().contractVersion())
                    || !roomId.equals(response.data().roomId())
                    || response.data().recipientUserIds() == null
                    || response.data().recipientUserIds().stream().anyMatch(Objects::isNull)
                    || response.data().recipientUserIds().stream().distinct().count()
                    != response.data().recipientUserIds().size()) {
                throw unavailable();
            }
            return response.data().recipientUserIds();
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Room event recipient lookup requires authentication");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ROOM_EVENT_RECIPIENTS_UNAUTHORIZED",
                    "Room event recipient lookup is not authorized");
        } catch (RestClientException exception) {
            throw unavailable();
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
                "Room event recipient lookup requires authentication");
    }

    private static DomainException unavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_RECIPIENTS_UNAVAILABLE",
                "Booking could not resolve affected room-event recipients");
    }

    private static RestClient.Builder timeoutBoundBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory);
    }

    private record Request(UUID roomId, UUID officeBuildingId, Instant startsAt, Instant endsAt,
                           String contractVersion) {
    }

    private record ApiResponse(boolean success, Data data) {
    }

    private record Data(String contractVersion, UUID roomId, List<UUID> recipientUserIds) {
    }
}
