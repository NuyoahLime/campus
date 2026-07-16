package com.campusguinness.media.internal.domain;

public final class InvalidMediaStateTransitionException extends RuntimeException {
    public InvalidMediaStateTransitionException(MediaInternalStatus current, String action) {
        super("Cannot " + action + " from internal status " + current);
    }

    public InvalidMediaStateTransitionException(MediaPublicStatus current, String action) {
        super("Cannot " + action + " from public status " + current);
    }
}
