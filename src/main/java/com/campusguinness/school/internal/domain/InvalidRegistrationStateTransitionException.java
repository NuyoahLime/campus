package com.campusguinness.school.internal.domain;

public final class InvalidRegistrationStateTransitionException extends RuntimeException {
    public InvalidRegistrationStateTransitionException(RegistrationStatus current, String action) {
        super("Cannot " + action + " from status " + current);
    }
}
