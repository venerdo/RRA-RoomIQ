package rw.rra.roomiq.booking.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import rw.rra.roomiq.common.web.DomainException;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PublicShareLinkRateLimitInterceptor implements HandlerInterceptor {
    private static final int REQUESTS_PER_WINDOW = 60;
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger trackedClients = new AtomicInteger();
    private final AtomicLong requestCount = new AtomicLong();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!allow(client, System.nanoTime())) {
            throw new DomainException(HttpStatus.TOO_MANY_REQUESTS, "PUBLIC_SHARE_RATE_LIMITED",
                    "Public meeting-card request limit exceeded");
        }
        return true;
    }

    boolean allow(String client, long nowNanos) {
        if (requestCount.incrementAndGet() % 256 == 0 || trackedClients.get() >= MAX_TRACKED_CLIENTS) {
            removeExpired(nowNanos);
        }
        Bucket bucket = buckets.get(client);
        if (bucket == null) {
            if (!reserveClientSlot()) {
                removeExpired(nowNanos);
                if (!reserveClientSlot()) {
                    return false;
                }
            }
            Bucket candidate = new Bucket(nowNanos);
            Bucket existing = buckets.putIfAbsent(client, candidate);
            if (existing == null) {
                bucket = candidate;
            } else {
                trackedClients.decrementAndGet();
                bucket = existing;
            }
        }
        return bucket.allow(nowNanos);
    }

    private boolean reserveClientSlot() {
        while (true) {
            int current = trackedClients.get();
            if (current >= MAX_TRACKED_CLIENTS) {
                return false;
            }
            if (trackedClients.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void removeExpired(long nowNanos) {
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Bucket> entry = iterator.next();
            if (entry.getValue().expired(nowNanos) && buckets.remove(entry.getKey(), entry.getValue())) {
                trackedClients.decrementAndGet();
            }
        }
    }

    private static final class Bucket {
        private long windowStartedAt;
        private int requests;

        private Bucket(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }

        private synchronized boolean allow(long nowNanos) {
            if (nowNanos - windowStartedAt >= WINDOW_NANOS || nowNanos < windowStartedAt) {
                windowStartedAt = nowNanos;
                requests = 0;
            }
            if (requests >= REQUESTS_PER_WINDOW) {
                return false;
            }
            requests++;
            return true;
        }

        private synchronized boolean expired(long nowNanos) {
            return nowNanos - windowStartedAt >= WINDOW_NANOS || nowNanos < windowStartedAt;
        }
    }
}
