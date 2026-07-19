package com.campusguinness.identity.application.port;

import com.campusguinness.identity.application.exception.InvalidPasswordException;

import java.nio.charset.StandardCharsets;

/**
 * Password validation rules, shared by ordinary user creation and admin bootstrap.
 * Domain layer must never depend on this.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    /**
     * Validate a raw password. Does NOT trim — leading/trailing spaces are part of the password.
     *
     * @throws InvalidPasswordException with a rule code (never the raw password)
     */
    public static void validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPasswordException("PASSWORD_BLANK");
        }
        if (raw.length() < 8) {
            throw new InvalidPasswordException("PASSWORD_TOO_SHORT");
        }
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 72) {
            throw new InvalidPasswordException("PASSWORD_TOO_LONG");
        }
    }
}
