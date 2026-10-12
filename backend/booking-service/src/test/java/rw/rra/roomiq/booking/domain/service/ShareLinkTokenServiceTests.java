package rw.rra.roomiq.booking.domain.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import rw.rra.roomiq.common.web.DomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareLinkTokenServiceTests {
    private static final String V1_SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String V2_SECRET = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Test
    void deterministicTokenReplaysAcrossSafeKeyRotationWhileRetainedVersionIsConfigured() {
        UUID creator = UUID.randomUUID();
        String idempotencyKey = "share-link-rotation";
        ShareLinkTokenService beforeRotation = new ShareLinkTokenService("v1=" + V1_SECRET, "v1");
        String original = beforeRotation.createToken(creator, idempotencyKey);

        ShareLinkTokenService afterRotation =
                new ShareLinkTokenService("v1=" + V1_SECRET + ",v2=" + V2_SECRET, "v2");
        String rotated = afterRotation.createToken(creator, "new-share-link");

        assertThat(original).startsWith("v1.v1.");
        assertThat(rotated).startsWith("v1.v2.");
        assertThat(afterRotation.reproduceToken(creator, idempotencyKey,
                ShareLinkTokenService.hashToken(original))).isEqualTo(original);
    }

    @Test
    void invalidOrUnretainedSecretFailsClosedWithoutMintingAReplacement() {
        UUID creator = UUID.randomUUID();
        ShareLinkTokenService missing = new ShareLinkTokenService("", "v1");
        assertUnavailable(() -> missing.createToken(creator, "key"));

        ShareLinkTokenService noLongerRetained =
                new ShareLinkTokenService("v2=" + V2_SECRET, "v2");
        assertUnavailable(() -> noLongerRetained.reproduceToken(creator, "key",
                ShareLinkTokenService.hashToken("v1.v1." + "A".repeat(43))));

        ShareLinkTokenService invalid = new ShareLinkTokenService("v1=short", "v1");
        assertUnavailable(() -> invalid.createToken(creator, "key"));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation) {
        assertThatThrownBy(invocation)
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    DomainException exception = (DomainException) error;
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }
}
