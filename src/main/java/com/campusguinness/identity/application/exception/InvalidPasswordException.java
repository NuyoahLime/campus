package com.campusguinness.identity.application.exception;

/**
 * Thrown when a password fails validation rules.
 * Message contains the rule code (e.g. PASSWORD_TOO_SHORT), never the raw password.
 */
public final class InvalidPasswordException extends IllegalArgumentException {

    public InvalidPasswordException(String ruleCode) {
        super(ruleCode);
    }
}
