package rw.rra.roomiq.common.web;

import java.time.Instant;
import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, Object> metadata,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Map.of(), Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data, Map<String, Object> metadata) {
        return new ApiResponse<>(true, message, data, metadata, Instant.now());
    }
}
