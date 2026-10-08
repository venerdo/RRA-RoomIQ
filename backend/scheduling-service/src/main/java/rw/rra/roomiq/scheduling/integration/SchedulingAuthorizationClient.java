package rw.rra.roomiq.scheduling.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import rw.rra.roomiq.common.web.DomainException;

@Component
public class SchedulingAuthorizationClient {
    private final RestClient identityClient;

    public SchedulingAuthorizationClient(@Value("${roomiq.identity.url:http://localhost:8081}") String identityUrl) {
        this.identityClient = RestClient.create(identityUrl);
    }

    public void authorize(String bearerToken, String action) {
        try {
            identityClient.post()
                    .uri("/api/v1/internal/authorization/scheduling")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SchedulingAuthorizationRequest(action))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new DomainException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED", "Authentication is required");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new DomainException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED", "Scheduling access is denied");
        } catch (RestClientException exception) {
            throw new DomainException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "IDENTITY_AUTHORIZATION_UNAVAILABLE", "Identity authorization is unavailable");
        }
    }

    private record SchedulingAuthorizationRequest(String action) {
    }
}