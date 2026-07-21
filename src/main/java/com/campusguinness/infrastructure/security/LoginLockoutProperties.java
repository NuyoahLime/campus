package com.campusguinness.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for account lockout mechanism.
 * Default: disabled. Production thresholds must be confirmed separately.
 */
@ConfigurationProperties(prefix = "campus-guinness.security.login-lockout")
public record LoginLockoutProperties(
        boolean enabled,
        int failureThreshold,
        java.time.Duration lockDuration,
        boolean resetFailuresOnSuccess
) {
    public LoginLockoutProperties {
        if (failureThreshold <= 0) failureThreshold = 5;
        if (lockDuration == null) lockDuration = java.time.Duration.ofMinutes(15);
    }

    /** Validate when enabled. */
    public void validate() {
        if (enabled && failureThreshold <= 0) {
            throw new IllegalStateException("login-lockout.failure-threshold must be > 0 when enabled");
        }
        if (enabled && (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero())) {
            throw new IllegalStateException("login-lockout.lock-duration must be positive when enabled");
        }
    }
}
