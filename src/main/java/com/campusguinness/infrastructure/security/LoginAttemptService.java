package com.campusguinness.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks failed credential attempts and enforces temporary login lockout.
 *
 * <p>Rules:</p>
 * <ul>
 *     <li>Five consecutive bad-credential attempts lock the account.</li>
 *     <li>The temporary lock lasts ten minutes.</li>
 *     <li>Attempts during an active lock do not extend the lock.</li>
 *     <li>After lock expiry, a new failure window starts from one.</li>
 *     <li>Only NORMAL accounts participate in temporary lockout.</li>
 * </ul>
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final long LOCK_DURATION_MINUTES = 10;

    private final JdbcTemplate jdbc;
    private final LoginNameNormalizer normalizer;

    public LoginAttemptService(JdbcTemplate jdbc, LoginNameNormalizer normalizer) {
        this.jdbc = jdbc;
        this.normalizer = normalizer;
    }

    /**
     * Reset temporary lockout state after credentials are verified.
     * <p>
     * The update must affect exactly one NORMAL account. Otherwise,
     * authentication must stop before a SecurityContext is persisted.
     */
    @Transactional
    public void recordSuccess(String rawUsername) {
        String username = normalizer.normalize(rawUsername);

        int updated = jdbc.update("""
                UPDATE users
                SET login_failures = 0,
                    locked_until = NULL,
                    updated_at = now()
                WHERE username = ?
                  AND account_status = 'NORMAL'
                """,
                username);

        if (updated != 1) {
            throw new AuthenticationStateUnavailableException(
                    "Authentication state could not be reset.");
        }
    }

    /**
     * Record one bad-credential attempt.
     * <p>
     * Unknown usernames and non-NORMAL accounts update zero rows.
     * An account already under an active temporary lock is left unchanged.
     */
    @Transactional
    public void recordFailure(String rawUsername) {
        String username = normalizer.normalize(rawUsername);

        jdbc.update("""
                UPDATE users
                SET login_failures = CASE
                        WHEN locked_until IS NOT NULL
                             AND locked_until <= now()
                        THEN 1
                        ELSE login_failures + 1
                    END,

                    locked_until = CASE
                        WHEN locked_until IS NOT NULL
                             AND locked_until <= now()
                        THEN NULL

                        WHEN login_failures + 1 >= ?
                        THEN now() + (? * INTERVAL '1 minute')

                        ELSE NULL
                    END,

                    updated_at = now()

                WHERE username = ?
                  AND account_status = 'NORMAL'
                  AND (
                      locked_until IS NULL
                      OR locked_until <= now()
                  )
                """,
                MAX_FAILURES, LOCK_DURATION_MINUTES, username);
    }
}
