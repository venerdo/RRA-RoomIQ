package rw.rra.roomiq.scheduling.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;

@Component
public class SchedulingAuthorizationClient {
    private final RestClient identityClient;

    public SchedulingAuthorizationClient(@Value("${roomiq.identity.url:http://localhost:8081}") String identityUrl) {
        this.identityClient = RestClient.create(identityUrl);
    }

    public UUID authorize(String bearerToken, String action) {
        try {
            SchedulingAuthorizationResponse response = identityClient.post()
                    .uri("/api/v1/internal/authorization/scheduling")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SchedulingAuthorizationRequest(action))
                    .retrieve()
                    .body(SchedulingAuthorizationResponse.class);
            if (response == null || response.actorUserId() == null) {
                throw unavailable();
            }
            return response.actorUserId();
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new DomainException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED", "Authentication is required");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED", "Scheduling access is denied");
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private record SchedulingAuthorizationRequest(String action) {
    }

    private record SchedulingAuthorizationResponse(UUID actorUserId) {
    }

    private static DomainException unavailable() {
        return new DomainException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "IDENTITY_AUTHORIZATION_UNAVAILABLE", "Identity authorization is unavailable");
    }
}