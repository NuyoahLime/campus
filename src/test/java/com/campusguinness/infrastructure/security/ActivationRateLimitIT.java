package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import static org.assertj.core.api.Assertions.*;

class ActivationRateLimitIT {
    @Test void rateLimiterEnforces15Minute5AttemptWindow() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        var limiter = new ActivationRateLimiter(clock);
        for (int i = 0; i < 5; i++) limiter.recordFailure("test", "1.2.3.4");
        assertThat(limiter.isRateLimited("test", "1.2.3.4")).isTrue();
    }

    @Test void successClearsRateLimit() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        var limiter = new ActivationRateLimiter(clock);
        for (int i = 0; i < 5; i++) limiter.recordFailure("test", "1.2.3.4");
        limiter.clear("test", "1.2.3.4");
        assertThat(limiter.isRateLimited("test", "1.2.3.4")).isFalse();
    }
}
