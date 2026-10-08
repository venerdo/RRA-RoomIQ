package rw.rra.roomiq.identity.domain.dto;

import java.time.Instant;

public record AuthTokenResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresIn,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UserResponse user
) {
}
