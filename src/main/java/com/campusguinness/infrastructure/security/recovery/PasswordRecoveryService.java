package com.campusguinness.infrastructure.security.recovery;

import com.campusguinness.identity.application.port.PasswordPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates password recovery: validate → lock → verify → encode → update → invalidate sessions.
 * All in a single transaction — session failure rolls back the password update.
 */
@Service
public class PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final long RECOVERY_LOCK_KEY = 987654321L;

    private final JdbcPasswordRecoveryStore store;
    private final PasswordEncoder encoder;

    public PasswordRecoveryService(JdbcPasswordRecoveryStore store, PasswordEncoder encoder) {
        this.store = store;
        this.encoder = encoder;
    }

    @Transactional
    public PasswordRecoveryResult recover(PasswordRecoveryProperties props) {
        // 1. Acquire transaction-scoped advisory lock
        store.acquireLock(RECOVERY_LOCK_KEY);

        // 2. Find and verify target user
        var target = store.findTarget(props.getTargetUserId())
                .orElse(null);
        if (target == null) {
            log.warn("Recovery: target user {} not found", props.getTargetUserId());
            return PasswordRecoveryResult.failure(30);
        }

        // 3. Strong match checks
        if (!props.getTargetUserId().equals(target.id())) {
            log.warn("Recovery: ID mismatch");
            return PasswordRecoveryResult.failure(32);
        }
        if (!props.getTargetUsername().equals(target.username())) {
            log.warn("Recovery: username mismatch expected={} actual={}", props.getTargetUsername(), target.username());
            return PasswordRecoveryResult.failure(32);
        }
        if (!props.getExpectedStatus().equals(target.status())) {
            log.warn("Recovery: status mismatch expected={} actual={}", props.getExpectedStatus(), target.status());
            return PasswordRecoveryResult.failure(33);
        }
        if (!props.getExpectedPlatformRole().equals(target.platformRole())) {
            log.warn("Recovery: role mismatch expected={} actual={}", props.getExpectedPlatformRole(), target.platformRole());
            return PasswordRecoveryResult.failure(34);
        }

        // 4. Validate password
        try {
            PasswordPolicy.validate(props.getNewPassword());
        } catch (Exception e) {
            log.warn("Recovery: password policy rejected");
            return PasswordRecoveryResult.failure(40);
        }

        // 5. Encode and update
        String newHash = encoder.encode(props.getNewPassword());
        int sessionsDeleted = 0;

        // 6. Invalidate sessions first, then update password — in same transaction
        if (props.isInvalidateExistingSessions()) {
            sessionsDeleted = store.deleteSessions(target.username());
        }

        int updated = store.updatePasswordHash(target.id(), target.username(), target.status(), target.platformRole(), newHash);
        if (updated != 1) {
            log.error("Recovery: unexpected update count {}", updated);
            throw new IllegalStateException("Unexpected password update count: " + updated);
        }

        var result = PasswordRecoveryResult.success(sessionsDeleted);
        log.info("Recovery: success for user={} sessionsDeleted={}", target.username(), sessionsDeleted);
        return result;
    }
}
