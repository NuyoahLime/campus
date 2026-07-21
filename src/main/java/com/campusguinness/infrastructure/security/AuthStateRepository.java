package com.campusguinness.infrastructure.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Atomic JDBC operations for account lockout state.
 * Uses parameterized SQL — never trusts client-provided userId for auth updates.
 */
@Component
@Transactional
public class AuthStateRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AuthStateRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Auto-unlock if locked_until has expired. Called before password check.
     * @return true if an unlock was performed
     */
    public boolean unlockIfExpired(String normalizedUsername) {
        int updated = jdbc.update(
                "UPDATE users SET account_status = 'NORMAL', login_failures = 0, locked_until = NULL, version = version + 1 " +
                        "WHERE username = :uname AND account_status = 'LOCKED' AND locked_until IS NOT NULL AND locked_until <= :now",
                new MapSqlParameterSource()
                        .addValue("uname", normalizedUsername)
                        .addValue("now", Instant.now(clock)));
        return updated > 0;
    }

    /**
     * Atomically increment failure count. Reaches threshold → locks account.
     * Unknown usernames return 0 updated rows (not an error).
     */
    public int incrementFailures(String normalizedUsername, int threshold, Instant lockExpiry) {
        return jdbc.update(
                "UPDATE users SET login_failures = login_failures + 1, version = version + 1 " +
                        "WHERE username = :uname AND account_status = 'NORMAL' AND login_failures < :threshold",
                new MapSqlParameterSource()
                        .addValue("uname", normalizedUsername)
                        .addValue("threshold", threshold));

        // If the above updated 0 rows, the user either doesn't exist, is already locked,
        // or has already reached threshold. Check if we need to lock.
    }

    /**
     * Lock the account if failures have reached threshold. Idempotent.
     */
    public int lockIfThresholdReached(String normalizedUsername, int threshold, Instant lockExpiry) {
        return jdbc.update(
                "UPDATE users SET account_status = 'LOCKED', locked_until = :expiry, version = version + 1 " +
                        "WHERE username = :uname AND account_status = 'NORMAL' AND login_failures >= :threshold",
                new MapSqlParameterSource()
                        .addValue("uname", normalizedUsername)
                        .addValue("threshold", threshold)
                        .addValue("expiry", lockExpiry));
    }

    /** Reset failures on successful login. */
    public int resetFailures(UUID userId) {
        return jdbc.update(
                "UPDATE users SET login_failures = 0, locked_until = NULL, version = version + 1 " +
                        "WHERE id = :id AND account_status = 'NORMAL'",
                new MapSqlParameterSource().addValue("id", userId));
    }
}
