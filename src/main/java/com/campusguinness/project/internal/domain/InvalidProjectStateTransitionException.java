package com.campusguinness.project.internal.domain;

/** Thrown when an invalid state transition is attempted on a ChallengeProject. */
public final class InvalidProjectStateTransitionException extends RuntimeException {
    public InvalidProjectStateTransitionException(ProjectStatus current, String action) {
        super("Cannot " + action + " from status " + current);
    }
}
