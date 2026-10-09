package rw.rra.roomiq.scheduling.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
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
import jakarta.servlet.http.HttpServletRequest;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Instant;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

@Component
public class HttpBookingOccupancyClient implements BookingOccupancyClient {
    private static final ParameterizedTypeReference<ApiResponse<OccupancySnapshot>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient bookingClient;

    @Autowired
    public HttpBookingOccupancyClient(
            @Value("${roomiq.booking.url:http://localhost:8085}") String bookingUrl) {
        this(timeoutBoundBuilder(), bookingUrl);
    }

    HttpBookingOccupancyClient(RestClient.Builder builder, String bookingUrl) {
        this.bookingClient = builder.baseUrl(bookingUrl).build();
    }

    @Override
    public OccupancySnapshot occupancy(List<UUID> roomIds, Instant startsAt, Instant endsAt) {
        String authorization = currentAuthorization();
        try {
            ApiResponse<OccupancySnapshot> response = bookingClient.post()
                    .uri("/api/v1/internal/availability/occupancy")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OccupancyQuery(roomIds, startsAt, endsAt, "v1"))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (response == null || !response.success() || response.data() == null) {
                throw unavailable();
            }
            return response.data();
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Booking occupancy access requires authentication");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "BOOKING_OCCUPANCY_ACCESS_DENIED",
                    "Booking occupancy access is denied");
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
                "Authentication is required to retrieve Booking occupancy");
    }

    private static DomainException unavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_OCCUPANCY_UNAVAILABLE",
                "Authoritative Booking occupancy is unavailable; no availability is being claimed");
    }

    private static RestClient.Builder timeoutBoundBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory);
    }

    private record OccupancyQuery(List<UUID> roomIds, Instant startsAt, Instant endsAt, String contractVersion) {
    }
}
