package com.campusguinness.appeal.internal.domain;

public final class InvalidAppealStateTransitionException extends RuntimeException {
    public InvalidAppealStateTransitionException(AppealStatus current, String action) {
        super("Cannot " + action + " from status " + current);
    }
}
