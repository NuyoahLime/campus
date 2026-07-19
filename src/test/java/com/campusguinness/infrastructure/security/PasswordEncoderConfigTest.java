package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test void isBCrypt() {
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test void encodesAndMatches() {
        String raw = "mySecurePassword123";
        String encoded = encoder.encode(raw);
        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoded).startsWith("$2a$");
        assertThat(encoder.matches(raw, encoded)).isTrue();
    }

    @Test void wrongPasswordDoesNotMatch() {
        String encoded = encoder.encode("correctPassword");
        assertThat(encoder.matches("wrongPassword", encoded)).isFalse();
    }

    @Test void eachEncodeProducesDifferentOutput() {
        String raw = "samePassword";
        String enc1 = encoder.encode(raw);
        String enc2 = encoder.encode(raw);
        assertThat(enc1).isNotEqualTo(enc2); // different salt each time
        assertThat(encoder.matches(raw, enc1)).isTrue();
        assertThat(encoder.matches(raw, enc2)).isTrue();
    }
}
