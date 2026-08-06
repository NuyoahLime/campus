package com.campusguinness.identity.internal.persistence;

import com.campusguinness.PostgreSqlIntegrationTestSupport;
import com.campusguinness.identity.application.port.LoginCredentialCommandPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoginCredentialCommandAdapterIT extends PostgreSqlIntegrationTestSupport {

    @Autowired LoginCredentialCommandPort credentials;
    @Autowired JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES (?,?,?,?,?)",
                userId, "login-credential-" + userId.toString().substring(0, 8), "hash", "NORMAL", "SUPER_ADMIN");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void fifthFailureSetsTemporaryLockWithoutChangingBusinessStatus() {
        for (int i = 0; i < 5; i++) {
            credentials.recordPasswordFailure(userId);
        }

        assertThat(loginFailures()).isEqualTo(5);
        assertThat(lockedUntil()).isAfter(Instant.now());
        assertThat(accountStatus()).isEqualTo("NORMAL");
    }

    @Test
    void failureDuringActiveLockDoesNotExtendLockOrCounter() {
        for (int i = 0; i < 5; i++) {
            credentials.recordPasswordFailure(userId);
        }
        Instant firstLock = lockedUntil();

        credentials.recordPasswordFailure(userId);

        assertThat(loginFailures()).isEqualTo(5);
        assertThat(lockedUntil()).isEqualTo(firstLock);
    }

    @Test
    void failureAfterLockExpiryStartsNewFailureWindow() {
        jdbc.update("UPDATE users SET login_failures = 5, locked_until = now() - INTERVAL '1 minute' WHERE id = ?",
                userId);

        credentials.recordPasswordFailure(userId);

        assertThat(loginFailures()).isEqualTo(1);
        assertThat(lockedUntil()).isNull();
        assertThat(accountStatus()).isEqualTo("NORMAL");
    }

    @Test
    void successAfterLockExpiryClearsFailuresAndExpiredLock() {
        jdbc.update("UPDATE users SET login_failures = 5, locked_until = now() - INTERVAL '1 minute' WHERE id = ?",
                userId);

        credentials.resetPasswordFailures(userId);

        assertThat(loginFailures()).isZero();
        assertThat(lockedUntil()).isNull();
    }

    @Test
    void successDuringActiveLockDoesNotClearLockWindow() {
        for (int i = 0; i < 5; i++) {
            credentials.recordPasswordFailure(userId);
        }
        Instant firstLock = lockedUntil();

        credentials.resetPasswordFailures(userId);

        assertThat(loginFailures()).isEqualTo(5);
        assertThat(lockedUntil()).isEqualTo(firstLock);
    }

    private int loginFailures() {
        return jdbc.queryForObject("SELECT login_failures FROM users WHERE id = ?", Integer.class, userId);
    }

    private Instant lockedUntil() {
        return jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?", Instant.class, userId);
    }

    private String accountStatus() {
        return jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, userId);
    }
}
