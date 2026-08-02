package com.campusguinness.infrastructure.security;

import com.campusguinness.identity.application.exception.InvalidPasswordException;
import com.campusguinness.identity.application.exception.UsernameAlreadyExistsException;
import com.campusguinness.identity.application.port.PasswordPolicy;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class AuthenticationInputValidationTest {

    private final Validator validator;

    AuthenticationInputValidationTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ── LoginRequest ──

    @Test void loginRequestValid() {
        var violations = validator.validate(new LoginRequest("user", "password123"));
        assertThat(violations).isEmpty();
    }

    @Test void loginRequestEmptyUsername() {
        var violations = validator.validate(new LoginRequest("", "password123"));
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username"))).isTrue();
    }

    @Test void loginRequestEmptyPassword() {
        var violations = validator.validate(new LoginRequest("user", ""));
        assertThat(violations).isNotEmpty();
    }

    @Test void loginRequestUsernameExceeds100Chars() {
        var violations = validator.validate(new LoginRequest("x".repeat(101), "password123"));
        assertThat(violations).isNotEmpty();
    }

    @Test void loginPasswordOver72Utf8BytesIsRejected() {
        // 69 ASCII chars + emoji = 69 + 4 = 73 UTF-8 bytes, but only 71 Java chars
        String password = "a".repeat(69) + "😀";
        assertThat(password.length()).isEqualTo(71);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);
        var violations = validator.validate(new LoginRequest("user", password));
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password"))).isTrue();
    }

    @Test void loginPasswordExactly72Utf8BytesIsValid() {
        // 68 ASCII chars + emoji = 68 + 4 = 72 UTF-8 bytes, 70 Java chars
        String password = "a".repeat(68) + "😀";
        assertThat(password.length()).isEqualTo(70);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        var violations = validator.validate(new LoginRequest("user", password));
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password"))).isFalse();
    }

    @Test void loginRequestToStringRedactsPassword() {
        var req = new LoginRequest("user", "secret123");
        String str = req.toString();
        assertThat(str).contains("user");
        assertThat(str).doesNotContain("secret123");
        assertThat(str).contains("[REDACTED]");
    }

    // ── CreateUserRequest ──

    @Test void createUserRequestValid() {
        var violations = validator.validate(
                new com.campusguinness.interfaces.web.user.CreateUserRequest("user", "password123"));
        assertThat(violations).isEmpty();
    }

    @Test void createUserRequestPasswordTooShort() {
        var violations = validator.validate(
                new com.campusguinness.interfaces.web.user.CreateUserRequest("user", "short"));
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("initialPassword"))).isTrue();
    }

    @Test void createUserRequestToStringRedactsPassword() {
        var req = new com.campusguinness.interfaces.web.user.CreateUserRequest("user", "secret123");
        String str = req.toString();
        assertThat(str).contains("user");
        assertThat(str).doesNotContain("secret123");
        assertThat(str).contains("[REDACTED]");
    }

    // ── PasswordPolicy ──

    @Test void passwordNullThrowsPASSWORD_BLANK() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("PASSWORD_BLANK");
    }

    @Test void passwordBlankThrowsPASSWORD_BLANK() {
        assertThatThrownBy(() -> PasswordPolicy.validate("   "))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("PASSWORD_BLANK");
    }

    @Test void passwordTooShortThrowsPASSWORD_TOO_SHORT() {
        assertThatThrownBy(() -> PasswordPolicy.validate("1234567"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("PASSWORD_TOO_SHORT");
    }

    @Test void password8CharsIsValid() {
        assertThatCode(() -> PasswordPolicy.validate("12345678")).doesNotThrowAnyException();
    }

    @Test void passwordChineseCharsUtf8Exceeds72Bytes() {
        // Each Chinese char is 3 bytes in UTF-8. 25 × 3 = 75 bytes > 72.
        String chinese = "密码".repeat(12) + "密"; // 25 Chinese chars = 75 bytes
        assertThat(chinese.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);
        assertThatThrownBy(() -> PasswordPolicy.validate(chinese))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("PASSWORD_TOO_LONG");
    }

    @Test void passwordChineseCharsUtf8Exactly72BytesIsValid() {
        // 24 Chinese chars × 3 = 72 bytes
        String chinese = "密".repeat(24);
        assertThat(chinese.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
        assertThatCode(() -> PasswordPolicy.validate(chinese)).doesNotThrowAnyException();
    }

    @Test void passwordLeadingTrailingSpacesArePreserved() {
        // Spaces are part of the password per spec
        assertThatCode(() -> PasswordPolicy.validate("  validPassword123  ")).doesNotThrowAnyException();
    }

    // ── UsernameAlreadyExistsException ──

    @Test void usernameAlreadyExistsExceptionIsSpecificType() {
        var ex = new UsernameAlreadyExistsException("testuser");
        assertThat(ex).isInstanceOf(IllegalArgumentException.class);
        assertThat(ex.getMessage()).contains("testuser");
    }
}
