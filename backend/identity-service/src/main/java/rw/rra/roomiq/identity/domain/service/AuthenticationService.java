package rw.rra.roomiq.identity.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.identity.domain.dto.AuthTokenResponse;
import rw.rra.roomiq.identity.domain.dto.LoginRequest;
import rw.rra.roomiq.identity.domain.dto.LogoutRequest;
import rw.rra.roomiq.identity.domain.dto.RefreshTokenRequest;
import rw.rra.roomiq.identity.domain.dto.UserResponse;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.UserSession;
import rw.rra.roomiq.identity.domain.entity.UserStatus;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;
import rw.rra.roomiq.identity.domain.repository.UserSessionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String INVALID_CREDENTIALS = "Email or password was not accepted";

    private final AppUserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final IdentityAuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final String dummyPasswordHash;

    public AuthenticationService(AppUserRepository userRepository,
                                 UserSessionRepository sessionRepository,
                                 IdentityAuditService auditService,
                                 PasswordEncoder passwordEncoder,
                                 JwtEncoder jwtEncoder,
                                 @Value("${roomiq.auth.access-token-ttl:PT15M}") Duration accessTokenTtl,
                                 @Value("${roomiq.auth.refresh-token-ttl:P7D}") Duration refreshTokenTtl) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID() + "-Dummy-password-9");
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
        AppUser user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String storedHash = user == null || user.getPasswordHash() == null
                ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);
        if (user == null || !passwordMatches || user.getStatus() != UserStatus.ACTIVE
                || user.getPasswordHash() == null) {
            log.warn("Authentication failed for account identifier");
            auditService.recordAuthenticationFailure();
            throw new DomainException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", INVALID_CREDENTIALS);
        }

        Instant now = Instant.now();
        user.recordSuccessfulLogin(now);
        userRepository.save(user);
        AuthTokenResponse response = issueTokens(user, ipAddress, userAgent, now);
        auditService.record("AUTHENTICATION_SUCCEEDED", user.getId(), "USER", user.getId(),
                user.getOfficeBuildingId(), "SUCCESS");
        return response;
    }

    @Transactional(noRollbackFor = DomainException.class)
    public AuthTokenResponse refresh(RefreshTokenRequest request, String ipAddress, String userAgent) {
        UserSession oldSession = sessionRepository.findByRefreshTokenHashForUpdate(hashToken(request.refreshToken()))
                .orElseThrow(this::invalidRefreshToken);
        Instant now = Instant.now();
        if (oldSession.getRevokedAt() != null || !oldSession.getExpiresAt().isAfter(now)
                || oldSession.getUser().getStatus() != UserStatus.ACTIVE
                || oldSession.getUser().getPasswordHash() == null) {
            if (oldSession.getRevokedAt() == null) {
                oldSession.revoke();
                sessionRepository.saveAndFlush(oldSession);
            }
            throw invalidRefreshToken();
        }

        AppUser user = oldSession.getUser();
        oldSession.revoke();
        sessionRepository.saveAndFlush(oldSession);
        AuthTokenResponse response = issueTokens(user, ipAddress, userAgent, now);
        auditService.record("REFRESH_TOKEN_ROTATED", user.getId(), "USER", user.getId(),
            user.getOfficeBuildingId(), "SUCCESS");
        return response;
    }

    @Transactional
    public void logout(LogoutRequest request) {
        sessionRepository.findByRefreshTokenHashForUpdate(hashToken(request.refreshToken())).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.revoke();
                sessionRepository.saveAndFlush(session);
                AppUser user = session.getUser();
                auditService.record("AUTHENTICATION_LOGOUT", user.getId(), "USER_SESSION",
                    session.getId(), user.getOfficeBuildingId(), "SUCCESS");
            }
        });
    }

    private AuthTokenResponse issueTokens(AppUser user, String ipAddress, String userAgent, Instant now) {
        String refreshToken = randomRefreshToken();
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        UserSession session = sessionRepository.saveAndFlush(new UserSession(
                user, hashToken(refreshToken), ipAddress, userAgent, now, refreshExpiresAt));

        Instant accessExpiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("rra-roomiq-identity")
                .subject(user.getId().toString())
                .id(session.getId().toString())
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .claim("email", user.getEmail())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();

        return new AuthTokenResponse("Bearer", accessToken, refreshToken, accessTokenTtl.toSeconds(),
                accessExpiresAt, refreshExpiresAt, UserResponse.from(user));
    }

    private String randomRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private DomainException invalidRefreshToken() {
        auditService.recordRefreshFailure();
        return new DomainException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Refresh token is invalid or expired");
    }
}
