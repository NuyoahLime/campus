package com.campusguinness.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public record AuthTokenProperties(
        Duration emailVerificationTtl,
        Duration passwordResetTtl
) {
    public AuthTokenProperties {
        if (emailVerificationTtl == null) {
            emailVerificationTtl = Duration.ofHours(24);
        }
        if (passwordResetTtl == null) {
            passwordResetTtl = Duration.ofMinutes(30);
        }
    }
}
