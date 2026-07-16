package com.campusguinness.activity.internal.domain;

public final class InvalidActivityApplicationStateTransitionException extends RuntimeException {
    public InvalidActivityApplicationStateTransitionException(ApplicationStatus current, String action) {
        super("Cannot " + action + " from status " + current);
    }
}
