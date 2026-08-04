package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRandomTokenGeneratorTest {

    private final SecureRandomTokenGenerator generator = new SecureRandomTokenGenerator();

    @Test
    void generatedTokenIsNotBlank() {
        assertThat(generator.generate()).isNotBlank();
    }

    @Test
    void generatedTokensAreDifferent() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @Test
    void generatedTokenIsUrlSafe() {
        assertThat(generator.generate()).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void generatedTokenHasSufficientEntropyLength() {
        assertThat(generator.generate()).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void generatedTokenHasNoPadding() {
        assertThat(generator.generate()).doesNotContain("=");
    }
}
