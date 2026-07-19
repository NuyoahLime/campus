package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.port.PasswordHasher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PasswordHasherTest {

    @Autowired private PasswordHasher hasher;

    @Test void hashProducesBCryptOutput() {
        String hash = hasher.hash("testPassword123");
        assertThat(hash).startsWith("$2a$");
        assertThat(hash).isNotEqualTo("testPassword123");
    }

    @Test void matchesReturnsTrueForCorrectPassword() {
        String hash = hasher.hash("myPassword");
        assertThat(hasher.matches("myPassword", hash)).isTrue();
    }

    @Test void matchesReturnsFalseForWrongPassword() {
        String hash = hasher.hash("correctPassword");
        assertThat(hasher.matches("wrongPassword", hash)).isFalse();
    }

    @Test void eachHashIsUnique() {
        String h1 = hasher.hash("samePassword");
        String h2 = hasher.hash("samePassword");
        assertThat(h1).isNotEqualTo(h2); // different salt
    }
}
