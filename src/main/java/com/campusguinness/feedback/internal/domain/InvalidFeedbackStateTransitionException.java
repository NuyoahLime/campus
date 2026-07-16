package com.campusguinness.feedback.internal.domain;

public final class InvalidFeedbackStateTransitionException extends RuntimeException {
    public InvalidFeedbackStateTransitionException(FeedbackStatus current, String action) {
        super("Cannot " + action + " from status " + current);
    }
}
