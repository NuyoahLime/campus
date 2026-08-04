package com.campusguinness.identity.application.service;

import com.campusguinness.identity.application.port.SecureTokenHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class ResendVerificationRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
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
        boolean limited = countRecent(emailKey) >= MAX_ATTEMPTS || countRecent(ipKey) >= MAX_ATTEMPTS;
        record(emailKey, limited);
        record(ipKey, limited);
        return limited;
    }

    private int countRecent(String key) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM activation_audit_logs
                WHERE username_normalized = ?
                  AND occurred_at >= now() - INTERVAL '15 minutes'
                """, Integer.class, key);
        return count == null ? 0 : count;
    }

    private void record(String key, boolean limited) {
        jdbc.update("""
                INSERT INTO activation_audit_logs(
                    id, username_normalized, result, failure_code, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                java.util.UUID.randomUUID(),
                key,
                limited ? "RATE_LIMITED" : "FAILURE",
                "RESEND_VERIFICATION",
                java.sql.Timestamp.from(clock.instant()));
    }
}
