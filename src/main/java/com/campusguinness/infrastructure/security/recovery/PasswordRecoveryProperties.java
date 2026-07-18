package com.campusguinness.infrastructure.security.recovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Environment-variable-only configuration for SUPER_ADMIN password recovery.
 * No defaults for security-critical fields. password excluded from toString().
 */
@ConfigurationProperties(prefix = "campus-guinness.security.admin-password-recovery")
public class PasswordRecoveryProperties {

    /** Must be explicitly set to "true". Default: false. */
    private boolean enabled;

    /** Target user UUID. Must match exactly. */
    private UUID targetUserId;

    /** Target username. Must match exactly. */
    private String targetUsername;

    /** Expected account status. Default: NORMAL. */
    private String expectedStatus = "NORMAL";

    /** Expected platform role. Default: SUPER_ADMIN. */
    private String expectedPlatformRole = "SUPER_ADMIN";

    /** New password — never logged, never printed. */
    private String newPassword;

    /** Invalidate the target user's existing sessions. Default: true. */
    private boolean invalidateExistingSessions = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public UUID getTargetUserId() { return targetUserId; }
    public void setTargetUserId(UUID v) { this.targetUserId = v; }

    public String getTargetUsername() { return targetUsername; }
    public void setTargetUsername(String v) { this.targetUsername = v; }

    public String getExpectedStatus() { return expectedStatus; }
    public void setExpectedStatus(String v) { this.expectedStatus = v; }

    public String getExpectedPlatformRole() { return expectedPlatformRole; }
    public void setExpectedPlatformRole(String v) { this.expectedPlatformRole = v; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String v) { this.newPassword = v; }

    public boolean isInvalidateExistingSessions() { return invalidateExistingSessions; }
    public void setInvalidateExistingSessions(boolean v) { this.invalidateExistingSessions = v; }

    @Override
    public String toString() {
        return "PasswordRecoveryProperties{enabled=" + enabled
                + ", targetUserId=" + targetUserId
                + ", targetUsername='" + targetUsername + "'"
                + ", expectedStatus='" + expectedStatus + "'"
                + ", expectedPlatformRole='" + expectedPlatformRole + "'"
                + ", newPassword=[REDACTED]"
                + ", invalidateExistingSessions=" + invalidateExistingSessions + "}";
    }
}
