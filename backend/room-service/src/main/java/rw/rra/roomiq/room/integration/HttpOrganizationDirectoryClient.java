package rw.rra.roomiq.room.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.rra.roomiq.common.web.ApiResponse;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;

@Component
public class HttpOrganizationDirectoryClient implements OrganizationDirectoryClient {
    private static final ParameterizedTypeReference<ApiResponse<BuildingReference>> BUILDING_RESPONSE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<FloorReference>> FLOOR_RESPONSE =
            new ParameterizedTypeReference<>() { };
        private static final ParameterizedTypeReference<ApiResponse<DepartmentReference>> DEPARTMENT_RESPONSE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public HttpOrganizationDirectoryClient(@Value("${organization.service.url:http://localhost:8082}") String baseUrl) {
        this(RestClient.builder(), baseUrl);
    }

    HttpOrganizationDirectoryClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void validateBuildingAndFloor(UUID officeBuildingId, UUID floorId) {
        String authorization = currentAuthorization();
        BuildingReference building = get("/api/v1/office-buildings/{id}", officeBuildingId,
                authorization, BUILDING_RESPONSE).data();
        FloorReference floor = get("/api/v1/floors/{id}", floorId, authorization, FLOOR_RESPONSE).data();

        if (building == null || !building.active() || !officeBuildingId.equals(building.id())) {
            throw invalidReference("Office building is missing or inactive");
        }
        if (floor == null || !floor.active() || !floorId.equals(floor.id())) {
            throw invalidReference("Floor is missing or inactive");
        }
        if (!officeBuildingId.equals(floor.officeBuildingId())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "ROOM_FLOOR_BUILDING_MISMATCH",
                    "Floor does not belong to the specified office building");
        }
    }

    @Override
    public void validateOfficeBuilding(UUID officeBuildingId) {
        BuildingReference building = get("/api/v1/office-buildings/{id}", officeBuildingId,
                currentAuthorization(), BUILDING_RESPONSE).data();
        if (building == null || !building.active() || !officeBuildingId.equals(building.id())) {
            throw invalidReference("Office building is missing or inactive");
        }
    }

    @Override
    public void validateDepartment(UUID departmentId, UUID expectedOfficeBuildingId) {
        DepartmentReference department = get("/api/v1/departments/{id}", departmentId,
                currentAuthorization(), DEPARTMENT_RESPONSE).data();
        if (department == null || !departmentId.equals(department.id()) || !"ACTIVE".equals(department.status())) {
            throw invalidReference("Department is missing or inactive");
        }
        if (department.officeBuildingId() != null
                && !department.officeBuildingId().equals(expectedOfficeBuildingId)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "ROOM_RULE_DEPARTMENT_SCOPE_MISMATCH",
                    "Department is outside the room rule building scope");
        }
    }

    private <T> ApiResponse<T> get(String path, UUID id, String authorization,
                                  ParameterizedTypeReference<ApiResponse<T>> responseType) {
        try {
            ApiResponse<T> response = restClient.get()
                    .uri(path, id)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(responseType);
            if (response == null || !response.success() || response.data() == null) {
                throw unavailable();
            }
            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            throw invalidReference("Organization reference was not found");
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "Organization access requires authentication");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED",
                    "Organization access is denied");
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
                "Authentication is required to validate Organization references");
    }

    private static DomainException invalidReference(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "ORGANIZATION_REFERENCE_INVALID", message);
    }

    private static DomainException unavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "ORGANIZATION_SERVICE_UNAVAILABLE",
                "Organization reference validation is unavailable");
    }

    private record BuildingReference(UUID id, boolean active) { }

    private record FloorReference(UUID id, UUID officeBuildingId, boolean active) { }

    private record DepartmentReference(UUID id, UUID officeBuildingId, String status) { }
}