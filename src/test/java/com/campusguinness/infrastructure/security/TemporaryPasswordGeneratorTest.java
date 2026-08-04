package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void generatedPasswordMeetsMinimumLength() {
        String pw = generator.generate();
        assertThat(pw).isNotBlank();
        assertThat(pw.length()).isGreaterThanOrEqualTo(16);
    }

    @Test
    void generatedPasswordsAreNotConstant() {
        String pw1 = generator.generate();
        String pw2 = generator.generate();
        assertThat(pw1).isNotEqualTo(pw2);
    }

    @Test
    void generatedPasswordContainsUppercaseLetter() {
        String pw = generator.generate();
        assertThat(pw).matches(".*[A-Z].*");
    }

    @Test
    void generatedPasswordContainsLowercaseLetter() {
        String pw = generator.generate();
        assertThat(pw).matches(".*[a-z].*");
    }

    @Test
    void generatedPasswordContainsDigit() {
        String pw = generator.generate();
        assertThat(pw).matches(".*[2-9].*");
    }

    @Test
    void generatedPasswordPassesPolicyValidation() {
        String pw = generator.generate();
        // Must not throw
        com.campusguinness.identity.application.port.PasswordPolicy.validate(pw);
    }

    @Test
    void toStringIsGeneric() {
        String s = generator.toString();
        // Must not expose any field values, seeds, or internal state
        assertThat(s).isEqualTo("TemporaryPasswordGenerator{...}");
    }
}
