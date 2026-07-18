package com.campusguinness.infrastructure.security.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-based store for password recovery operations.
 * Uses direct SQL for precise control over password_hash updates
 * and session deletions — bypasses the domain layer which
 * deliberately excludes password fields.
 */
@Component
class JdbcPasswordRecoveryStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcPasswordRecoveryStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    JdbcPasswordRecoveryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Read a recovery projection for the target user. */
    @Transactional(readOnly = true)
    public Optional<TargetUserProjection> findTarget(UUID userId) {
        var rows = jdbc.query(
                "SELECT id, username, account_status, platform_role FROM users WHERE id = :id",
                new MapSqlParameterSource("id", userId),
                (rs, rowNum) -> new TargetUserProjection(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("account_status"),
                        rs.getString("platform_role")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * Update only password_hash for the target user.
     * Uses strong matching on id + username + status + role to prevent accidental writes.
     * @return number of rows updated (must be exactly 1)
     */
    @Transactional
    public int updatePasswordHash(UUID userId, String username, String status, String role, String newHash) {
        return jdbc.update(
                "UPDATE users SET password_hash = :hash WHERE id = :id AND username = :uname AND account_status = :status AND platform_role = :role",
                new MapSqlParameterSource(Map.of(
                        "hash", newHash,
                        "id", userId,
                        "uname", username,
                        "status", status,
                        "role", role)));
    }

    /** Delete all sessions for the given principal name. Returns count of deleted rows. */
    @Transactional
    public int deleteSessions(String principalName) {
        // spring_session_attributes cascade-deletes via FK ON DELETE CASCADE
        return jdbc.update("DELETE FROM spring_session WHERE principal_name = :name",
                new MapSqlParameterSource("name", principalName));
    }

    /** Count sessions for any principal (for verification) */
    @Transactional(readOnly = true)
    public int countSessionsFor(String principalName) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM spring_session WHERE principal_name = :name",
                new MapSqlParameterSource("name", principalName), Integer.class);
        return c != null ? c : 0;
    }

    /** Count all sessions (for verification) */
    @Transactional(readOnly = true)
    public int countAllSessions() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM spring_session", Map.of(), Integer.class);
        return c != null ? c : 0;
    }

    /** Acquire a transaction-scoped advisory lock. */
    @Transactional
    public void acquireLock(long lockKey) {
        jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(" + lockKey + ")");
    }

    /** Read-only projection for target user verification. */
    public record TargetUserProjection(UUID id, String username, String status, String platformRole) {}
}
