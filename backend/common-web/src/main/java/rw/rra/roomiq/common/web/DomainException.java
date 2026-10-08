package rw.rra.roomiq.common.web;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = Objects.requireNonNull(status);
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("Domain error code must be a stable uppercase identifier");
        }
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}