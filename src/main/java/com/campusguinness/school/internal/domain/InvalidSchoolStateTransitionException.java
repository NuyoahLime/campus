package com.campusguinness.school.internal.domain;

public final class InvalidSchoolStateTransitionException extends RuntimeException {
    public InvalidSchoolStateTransitionException(SchoolStatus current, String action) {
        super("Cannot " + action + " from status " + current);
    }
}
