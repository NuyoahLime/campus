package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.application.port.LoginCredentialCommandPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
class LoginCredentialCommandAdapter implements LoginCredentialCommandPort {

    private static final int LOCK_THRESHOLD = 5;
    private static final long LOCK_MINUTES = 15L;

    private final JdbcTemplate jdbc;

    LoginCredentialCommandAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void recordPasswordFailure(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        jdbc.update("""
                UPDATE users
                SET login_failures = CASE
                        WHEN locked_until IS NOT NULL AND locked_until <= now()
                        THEN 1
                        ELSE login_failures + 1
                    END,
                    locked_until = CASE
                        WHEN locked_until IS NOT NULL AND locked_until <= now()
                        THEN NULL
                        WHEN login_failures + 1 >= ?
                        THEN now() + (? * INTERVAL '1 minute')
                        ELSE locked_until
                    END,
                    updated_at = now()
                WHERE id = ?
                  AND account_status = 'NORMAL'
                  AND (locked_until IS NULL OR locked_until <= now())
                """, LOCK_THRESHOLD, LOCK_MINUTES, userId);
    }

    @Override
    @Transactional
    public void resetPasswordFailures(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        jdbc.update("""
                UPDATE users
                SET login_failures = 0,
                    locked_until = NULL,
                    updated_at = now()
                WHERE id = ?
                  AND (locked_until IS NULL OR locked_until <= now())
                """, userId);
    }
}
