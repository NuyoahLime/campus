package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.SecureTokenHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;

@Component
public class ResendVerificationRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final JdbcTemplate jdbc;
    private final SecureTokenHasher hasher;
    private final Clock clock;

    public ResendVerificationRateLimiter(JdbcTemplate jdbc, SecureTokenHasher hasher, Clock clock) {
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public boolean isLimitedAndRecord(String emailNormalized, String clientIp) {
        String emailKey = "resend:email:" + hasher.hash(emailNormalized);
        String ipKey = "resend:ip:" + hasher.hash(clientIp == null ? "unknown" : clientIp);
        List<String> lockKeys = List.of(emailKey, ipKey).stream()
                .sorted()
                .toList();
        lockKeys.forEach(this::lockKey);

        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        boolean limited = countRecent(emailKey, windowStart) >= MAX_ATTEMPTS
                || countRecent(ipKey, windowStart) >= MAX_ATTEMPTS;
        record(emailKey, limited, now);
        record(ipKey, limited, now);
        return limited;
    }

    private void lockKey(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                ps -> ps.setString(1, key),
                rs -> null);
    }

    private int countRecent(String key, Instant windowStart) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM activation_audit_logs
                WHERE username_normalized = ?
                  AND occurred_at >= ?
                """, Integer.class, key, Timestamp.from(windowStart));
        return count == null ? 0 : count;
    }

    private void record(String key, boolean limited, Instant now) {
        jdbc.update("""
                INSERT INTO activation_audit_logs(
                    id, username_normalized, result, failure_code, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                java.util.UUID.randomUUID(),
                key,
                limited ? "RATE_LIMITED" : "FAILURE",
                "RESEND_VERIFICATION",
                Timestamp.from(now));
    }
}
