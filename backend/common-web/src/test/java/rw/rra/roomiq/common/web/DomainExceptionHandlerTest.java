package rw.rra.roomiq.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionHandlerTest {
    @Test
    void mapsDomainCodeStatusAndCorrelationIdToSharedError() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bookings");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "request-123");

        var response = new GlobalExceptionHandler().handleDomainException(
                new DomainException(HttpStatus.CONFLICT, "ROOM_NOT_AVAILABLE", "Room is unavailable"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).satisfies(error -> {
            assertThat(error.code()).isEqualTo("ROOM_NOT_AVAILABLE");
            assertThat(error.message()).isEqualTo("Room is unavailable");
            assertThat(error.path()).isEqualTo("/api/v1/bookings");
            assertThat(error.correlationId()).isEqualTo("request-123");
            assertThat(error.timestamp()).isNotNull();
        });
    }

    @Test
    void unexpectedFailuresDoNotExposeInternalExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "safe-error-trace");

        var response = new GlobalExceptionHandler().handleUnexpectedException(
                new IllegalStateException("database password leaked in exception text"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).satisfies(error -> {
            assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
            assertThat(error.message()).isEqualTo("An internal server error occurred");
            assertThat(error.correlationId()).isEqualTo("safe-error-trace");
            assertThat(error.toString()).doesNotContain("database password");
        });
    }
}