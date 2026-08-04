package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256SecureTokenHasherTest {

    private final Sha256SecureTokenHasher hasher = new Sha256SecureTokenHasher();

    @Test
    void sameTokenProducesSameHash() {
        assertThat(hasher.hash("raw-token")).isEqualTo(hasher.hash("raw-token"));
    }

    @Test
    void differentTokensProduceDifferentHashes() {
        assertThat(hasher.hash("raw-token-1")).isNotEqualTo(hasher.hash("raw-token-2"));
    }

    @Test
    void hashIs64LowercaseHexCharacters() {
        assertThat(hasher.hash("raw-token")).matches("^[0-9a-f]{64}$");
    }

    @Test
    void rawTokenIsNotContainedInHash() {
        assertThat(hasher.hash("raw-token")).doesNotContain("raw-token");
    }
}
