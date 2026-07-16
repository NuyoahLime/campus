package com.campusguinness.ranking.internal.domain;

public final class InvalidAuthorizationStateTransitionException extends RuntimeException {
    public InvalidAuthorizationStateTransitionException(AuthorizationStatus current, String action) {
        super("Cannot " + action + " from status " + current);
    }
}
