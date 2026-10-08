package rw.rra.roomiq.common.web;

public record ValidationError(
        String field,
        String message
) {
}