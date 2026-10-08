package rw.rra.roomiq.identity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rw.rra.roomiq.common.web.ApiError;
import rw.rra.roomiq.common.web.GlobalExceptionHandler;
import rw.rra.roomiq.identity.domain.service.IdentityAuditService;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentityAccessDeniedExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(IdentityAccessDeniedExceptionHandler.class);

    private final IdentityAuditService auditService;
    private final GlobalExceptionHandler sharedExceptionHandler;

    public IdentityAccessDeniedExceptionHandler(IdentityAuditService auditService,
                                                GlobalExceptionHandler sharedExceptionHandler) {
        this.auditService = auditService;
        this.sharedExceptionHandler = sharedExceptionHandler;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception,
                                                       HttpServletRequest request) {
        try {
            auditService.recordAuthorizationDenied();
        } catch (RuntimeException auditFailure) {
            log.warn("Authorization denial audit could not be recorded failureType={}",
                    auditFailure.getClass().getSimpleName());
        }
        return sharedExceptionHandler.handleAccessDenied(exception, request);
    }
}