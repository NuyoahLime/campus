package com.campusguinness.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActivationRateLimiter {

    private final ConcurrentHashMap<String, AttemptWindow> store = new ConcurrentHashMap<>();
    private final Clock clock;
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MINUTES = 15;

    public ActivationRateLimiter(Clock clock) { this.clock = clock; }

    public boolean isRateLimited(String normalizedUsername, String clientIp) {
        String key = normalizedUsername + "|" + clientIp;
        cleanup(key);
        var w = store.get(key);
        return w != null && w.count >= MAX_ATTEMPTS;
    }

    public void recordFailure(String normalizedUsername, String clientIp) {
        String key = normalizedUsername + "|" + clientIp;
        store.compute(key, (k, v) -> {
            if (v == null || clock.instant().isAfter(v.lastAttempt.plusSeconds(WINDOW_MINUTES * 60))) {
                return new AttemptWindow(1, clock.instant());
            }
            v.count++;
            v.lastAttempt = clock.instant();
            return v;
        });
    }

    public void clear(String normalizedUsername, String clientIp) {
        store.remove(normalizedUsername + "|" + clientIp);
    }

    private void cleanup(String key) {
        var w = store.get(key);
        if (w != null && clock.instant().isAfter(w.lastAttempt.plusSeconds(WINDOW_MINUTES * 60))) {
            store.remove(key);
        }
    }

    private static class AttemptWindow {
        int count;
        Instant lastAttempt;
        AttemptWindow(int c, Instant t) { this.count = c; this.lastAttempt = t; }
    }
}
