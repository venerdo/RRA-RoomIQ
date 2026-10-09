package rw.rra.roomiq.scheduling.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.common.web.DomainException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class HttpRoomAvailabilityClient implements RoomAvailabilityClient {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_ROOM_PAGES = 100;
    private static final ParameterizedTypeReference<ApiResponse<CatalogPage<RoomReference>>> ROOM_PAGE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<RoomRuleReference>>> ROOM_RULES =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<RoomFacilityReference>>> FACILITIES =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<MaintenancePeriodReference>>> MAINTENANCE =
            new ParameterizedTypeReference<>() { };

    private final RestClient roomClient;

    @Autowired
    public HttpRoomAvailabilityClient(
            @Value("${roomiq.room.url:http://localhost:8083}") String roomUrl) {
        this(timeoutBoundBuilder(), roomUrl);
    }

    HttpRoomAvailabilityClient(RestClient.Builder builder, String roomUrl) {
        this.roomClient = builder.baseUrl(roomUrl).build();
    }

    @Override
    public List<RoomReference> rooms(UUID officeBuildingId) {
        String authorization = currentAuthorization();
        List<RoomReference> rooms = new ArrayList<>();
        for (int page = 0; page < MAX_ROOM_PAGES; page++) {
            CatalogPage<RoomReference> result = get(
                    "/api/v1/rooms?officeBuildingId={buildingId}&status=AVAILABLE&page={page}&size={size}",
                    authorization, ROOM_PAGE, officeBuildingId, page, PAGE_SIZE);
            if (result.content() == null || result.totalPages() < 0 || result.totalPages() > MAX_ROOM_PAGES) {
                throw unavailable();
            }
            rooms.addAll(result.content());
            if (page + 1 >= result.totalPages()) {
                return List.copyOf(rooms);
            }
        }
        throw new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_AVAILABILITY_RESULT_TOO_LARGE",
                "Room inventory exceeds the supported availability search limit");
    }

    @Override
    public List<RoomRuleReference> roomRules(UUID roomId) {
        return get("/api/v1/rooms/{roomId}/rules", currentAuthorization(), ROOM_RULES, roomId);
    }

    @Override
    public List<RoomRuleReference> buildingRules(UUID officeBuildingId) {
        return get("/api/v1/office-buildings/{buildingId}/room-rules", currentAuthorization(),
                ROOM_RULES, officeBuildingId);
    }

    @Override
    public List<RoomFacilityReference> facilities(UUID roomId) {
        return get("/api/v1/rooms/{roomId}/facilities", currentAuthorization(), FACILITIES, roomId);
    }

    @Override
    public List<MaintenancePeriodReference> maintenancePeriods(UUID roomId) {
        return get("/api/v1/rooms/{roomId}/maintenance-periods", currentAuthorization(), MAINTENANCE, roomId);
    }

    private <T> T get(String path, String authorization, ParameterizedTypeReference<ApiResponse<T>> responseType,
                      Object... uriVariables) {
        try {
            ApiResponse<T> response = roomClient.get()
                    .uri(path, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(responseType);
            if (response == null || !response.success() || response.data() == null) {
                throw unavailable();
            }
            return response.data();
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Room availability access requires authentication");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ROOM_AVAILABILITY_ACCESS_DENIED",
                    "Room availability access is denied");
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
                "Authentication is required to retrieve Room availability data");
    }

    private static DomainException unavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_AVAILABILITY_UNAVAILABLE",
                "Current Room availability data is unavailable; no availability is being claimed");
    }

    private static RestClient.Builder timeoutBoundBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory);
    }

    private record CatalogPage<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, String sortBy, String sortDirection) {
    }
}
