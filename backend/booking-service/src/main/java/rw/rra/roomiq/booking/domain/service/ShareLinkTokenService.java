package rw.rra.roomiq.booking.domain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import rw.rra.roomiq.common.web.DomainException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ShareLinkTokenService {
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final String DERIVATION_DOMAIN = "RRA-ROOMIQ:BOOKING:MEETING-SHARE-LINK:CREATE:V1";

    private final String keyRingConfiguration;
    private final String activeKeyVersion;

    public ShareLinkTokenService(
            @Value("${roomiq.booking.share-token.key-ring:}") String keyRingConfiguration,
            @Value("${roomiq.booking.share-token.active-key-version:v1}") String activeKeyVersion) {
        this.keyRingConfiguration = keyRingConfiguration;
        this.activeKeyVersion = activeKeyVersion;
    }

    public String createToken(UUID creatorUserId, String idempotencyKey) {
        KeyRing keyRing = keyRing();
        return derive(keyRing.activeVersion(), keyRing.keys().get(keyRing.activeVersion()),
                creatorUserId, idempotencyKey);
    }

    public String reproduceToken(UUID creatorUserId, String idempotencyKey, String expectedTokenHash) {
        KeyRing keyRing = keyRing();
        byte[] expectedHash;
        try {
            expectedHash = HexFormat.of().parseHex(expectedTokenHash);
        } catch (IllegalArgumentException exception) {
            throw tokenReplayUnavailable();
        }
        for (Map.Entry<String, byte[]> entry : keyRing.keys().entrySet()) {
            String token = derive(entry.getKey(), entry.getValue(), creatorUserId, idempotencyKey);
            if (MessageDigest.isEqual(expectedHash, sha256(token))) {
                return token;
            }
        }
        throw tokenReplayUnavailable();
    }

    public static String hashToken(String token) {
        return HexFormat.of().formatHex(sha256(token));
    }

    private KeyRing keyRing() {
        if (keyRingConfiguration == null || keyRingConfiguration.isBlank()
                || activeKeyVersion == null || !VERSION.matcher(activeKeyVersion).matches()) {
            throw tokenSecretUnavailable();
        }
        Map<String, byte[]> keys = new LinkedHashMap<>();
        try {
            for (String configuredKey : keyRingConfiguration.split(",", -1)) {
                String[] parts = configuredKey.trim().split("=", -1);
                if (parts.length != 2 || !VERSION.matcher(parts[0]).matches() || parts[1].isBlank()
                        || keys.containsKey(parts[0])) {
                    throw tokenSecretUnavailable();
                }
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                if (decoded.length < 32 || !Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(decoded).equals(parts[1])) {
                    throw tokenSecretUnavailable();
                }
                keys.put(parts[0], decoded);
            }
        } catch (IllegalArgumentException exception) {
            throw tokenSecretUnavailable();
        }
        if (!keys.containsKey(activeKeyVersion)) {
            throw tokenSecretUnavailable();
        }
        return new KeyRing(keys, activeKeyVersion);
    }

    private static String derive(String keyVersion, byte[] secret, UUID creatorUserId, String idempotencyKey) {
        if (creatorUserId == null || idempotencyKey == null) {
            throw tokenSecretUnavailable();
        }
        String message = DERIVATION_DOMAIN + "\n" + keyVersion + "\n" + creatorUserId + "\n" + idempotencyKey;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String digest = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            return "v1." + keyVersion + "." + digest;
        } catch (GeneralSecurityException exception) {
            throw tokenSecretUnavailable();
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw tokenSecretUnavailable();
        }
    }

    private static DomainException tokenSecretUnavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "SHARE_TOKEN_SECRET_UNAVAILABLE",
                "Meeting share-link token configuration is unavailable");
    }

    private static DomainException tokenReplayUnavailable() {
        return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "SHARE_LINK_TOKEN_REPLAY_UNAVAILABLE",
                "The original meeting share-link token cannot be reproduced safely");
    }

    private record KeyRing(Map<String, byte[]> keys, String activeVersion) {
    }
}
