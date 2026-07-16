package com.campusguinness.result.internal.domain;

public final class InvalidResultStateTransitionException extends RuntimeException {
    public InvalidResultStateTransitionException(ResultInternalStatus current, String action) {
        super("Cannot " + action + " from internal status " + current);
    }

    public InvalidResultStateTransitionException(ResultPublicStatus current, String action) {
        super("Cannot " + action + " from public status " + current);
    }
}
