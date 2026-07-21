package com.campusguinness.infrastructure.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Atomic JDBC operations for account lockout state.
 * Uses Timestamp for JDBC compatibility — PostgreSQL timestamptz via NamedParameterJdbcTemplate.
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

    public boolean unlockIfExpired(String normalizedUsername) {
        int updated = jdbc.update(
                "UPDATE users SET account_status = 'NORMAL', login_failures = 0, locked_until = NULL, version = version + 1 " +
                        "WHERE username = :uname AND account_status = 'LOCKED' AND locked_until IS NOT NULL AND locked_until <= :now",
                new MapSqlParameterSource()
                        .addValue("uname", normalizedUsername)
                        .addValue("now", Timestamp.from(Instant.now(clock))));
        return updated > 0;
    }

    public int incrementAndLockIfNeeded(String normalizedUsername, int threshold, Instant lockExpiry) {
        return jdbc.update(
                "UPDATE users SET " +
                        "login_failures = login_failures + 1, " +
                        "account_status = CASE WHEN login_failures + 1 >= :threshold THEN 'LOCKED' ELSE account_status END, " +
                        "locked_until = CASE WHEN login_failures + 1 >= :threshold THEN :expiry ELSE locked_until END, " +
                        "version = version + 1 " +
                        "WHERE username = :uname AND account_status = 'NORMAL' AND login_failures < :threshold",
                new MapSqlParameterSource()
                        .addValue("uname", normalizedUsername)
                        .addValue("threshold", threshold)
                        .addValue("expiry", Timestamp.from(lockExpiry)));
    }

    public int resetFailures(UUID userId) {
        return jdbc.update(
                "UPDATE users SET login_failures = 0, locked_until = NULL, version = version + 1 " +
                        "WHERE id = :id AND account_status = 'NORMAL'",
                new MapSqlParameterSource().addValue("id", userId));
    }
}
