package com.campusguinness.infrastructure.security;

/**
 * Raised when authentication-related state cannot be updated safely.
 * <p>
 * The login request must fail closed and no authenticated session
 * may be persisted when this exception is raised.
 */
public final class AuthenticationStateUnavailableException extends RuntimeException {

    public AuthenticationStateUnavailableException(String message) {
        super(message);
    }

    public AuthenticationStateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
