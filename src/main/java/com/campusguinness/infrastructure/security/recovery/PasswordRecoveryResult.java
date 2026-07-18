package com.campusguinness.infrastructure.security.recovery;

/**
 * Result of a password recovery operation. Contains only safe fields.
 */
public record PasswordRecoveryResult(
        boolean success,
        int exitCode,
        int sessionsDeleted
) {
    public static PasswordRecoveryResult success(int sessionsDeleted) {
        return new PasswordRecoveryResult(true, 0, sessionsDeleted);
    }

    public static PasswordRecoveryResult failure(int exitCode) {
        return new PasswordRecoveryResult(false, exitCode, 0);
    }
}
