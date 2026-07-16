package com.campusguinness.identity.internal.domain;

public final class InvalidAccountStateTransitionException extends RuntimeException {
    public InvalidAccountStateTransitionException(AccountStatus current, String action) {
        super("Cannot " + action + " from account status " + current);
    }
}
