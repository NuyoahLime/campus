package com.campusguinness.score.internal.domain;

public final class InvalidScoreAttemptStateTransitionException extends RuntimeException {
    public InvalidScoreAttemptStateTransitionException(AttemptStatus current, String action) {
        super("Cannot " + action + " from status " + current);
    }
}
