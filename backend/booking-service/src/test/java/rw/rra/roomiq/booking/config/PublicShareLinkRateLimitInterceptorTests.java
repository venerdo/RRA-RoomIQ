package rw.rra.roomiq.booking.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicShareLinkRateLimitInterceptorTests {
    @Test
    void limitsEachClientAndAllowsRequestsInTheNextWindow() {
        PublicShareLinkRateLimitInterceptor interceptor = new PublicShareLinkRateLimitInterceptor();

        for (int request = 0; request < 60; request++) {
            assertThat(interceptor.allow("192.0.2.10", 1_000_000_000L + request)).isTrue();
        }
        assertThat(interceptor.allow("192.0.2.10", 2_000_000_000L)).isFalse();
        assertThat(interceptor.allow("192.0.2.11", 2_000_000_000L)).isTrue();
        assertThat(interceptor.allow("192.0.2.10", 61_000_000_001L)).isTrue();
    }
}
