package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class ActivationRateLimitTest {
    private Clock fixedClock;
    private ActivationRateLimiter limiter;

    @BeforeEach void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        limiter = new ActivationRateLimiter(fixedClock);
    }

    @Test void allows5FailuresBeforeRateLimit() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.isRateLimited("test", "1.2.3.4")).isFalse();
            limiter.recordFailure("test", "1.2.3.4");
        }
        assertThat(limiter.isRateLimited("test", "1.2.3.4")).isTrue();
    }

    @Test void clearRemovesLimit() {
        for (int i = 0; i < 5; i++) limiter.recordFailure("test", "1.2.3.4");
        limiter.clear("test", "1.2.3.4");
        assertThat(limiter.isRateLimited("test", "1.2.3.4")).isFalse();
    }

    @Test void concurrentRecordDoesNotLoseCount() throws Exception {
        var latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            new Thread(() -> { limiter.recordFailure("c", "x"); latch.countDown(); }).start();
        }
        latch.await();
        assertThat(limiter.isRateLimited("c", "x")).isTrue();
    }

    @Test void differentIpDoesNotShareCount() {
        for (int i = 0; i < 5; i++) limiter.recordFailure("test", "1.1.1.1");
        assertThat(limiter.isRateLimited("test", "2.2.2.2")).isFalse();
    }
}
