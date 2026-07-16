package com.campusguinness.activity.internal.domain;

public final class InvalidActivityStateTransitionException extends RuntimeException {
    public InvalidActivityStateTransitionException(ExecutionStatus current, String action) {
        super("Cannot " + action + " from execution status " + current);
    }

    public InvalidActivityStateTransitionException(PublicStatus current, String action) {
        super("Cannot " + action + " from public status " + current);
    }
}
