package rw.rra.roomiq.common.web;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        String path,
        int status,
        String correlationId,
        List<ValidationError> validationErrors,
        Instant timestamp
) {
}
