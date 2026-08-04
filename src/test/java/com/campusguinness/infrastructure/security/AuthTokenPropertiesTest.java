package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTokenPropertiesTest {

    @Test
    void defaultsAreApplied() {
        var props = new AuthTokenProperties(null, null);
        assertThat(props.emailVerificationTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(props.passwordResetTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void positiveDurationsAccepted() {
        var props = new AuthTokenProperties(Duration.ofHours(1), Duration.ofMinutes(5));
        assertThat(props.emailVerificationTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(props.passwordResetTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void zeroEmailVerificationTtlRejected() {
        assertThatThrownBy(() -> new AuthTokenProperties(Duration.ZERO, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePasswordResetTtlRejected() {
        assertThatThrownBy(() -> new AuthTokenProperties(Duration.ofHours(1), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
