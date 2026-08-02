package com.campusguinness.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks login failures and enforces temporary lockout.
 * <p>
 * Rules: 5 consecutive failures → locked for 10 minutes.<br>
 * Success resets the counter. Uses atomic database updates.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    static final int MAX_FAILURES = 5;
    static final long LOCK_DURATION_MINUTES = 10;

    private final JdbcTemplate jdbc;
    private final LoginNameNormalizer normalizer;

    public LoginAttemptService(JdbcTemplate jdbc, LoginNameNormalizer normalizer) {
        this.jdbc = jdbc;
        this.normalizer = normalizer;
    }

    /**
     * Record a successful login — reset failure counter and clear lock.
     */
    @Transactional
    public void recordSuccess(String rawUsername) {
        String username = normalizer.normalize(rawUsername);
        int updated = jdbc.update(
                "UPDATE users SET login_failures = 0, locked_until = NULL WHERE username = ?",
                username);
        if (updated > 0) {
            log.debug("Login success: reset lockout for {}", username);
        }
    }

    /**
     * Record a failed login attempt for a known user.
     * For non-existent usernames, normalize but the UPDATE affects 0 rows.
     */
    @Transactional
    public void recordFailure(String rawUsername) {
        String username = normalizer.normalize(rawUsername);
        int updated = jdbc.update(
                "UPDATE users SET login_failures = login_failures + 1, locked_until = CASE "
                        + "WHEN login_failures + 1 >= ? THEN now() + (? * INTERVAL '1 minute') "
                        + "ELSE locked_until END "
                        + "WHERE username = ?",
                MAX_FAILURES, LOCK_DURATION_MINUTES, username);
        if (updated > 0) {
            log.debug("Login failure recorded for {}", username);
        }
    }
}
