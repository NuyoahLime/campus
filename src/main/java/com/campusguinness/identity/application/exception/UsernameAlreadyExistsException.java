package com.campusguinness.identity.application.exception;

/**
 * Thrown when attempting to create a user with a username that already exists.
 */
public final class UsernameAlreadyExistsException extends IllegalArgumentException {

    public UsernameAlreadyExistsException(String username) {
        super("Username already exists: " + username);
    }
}
