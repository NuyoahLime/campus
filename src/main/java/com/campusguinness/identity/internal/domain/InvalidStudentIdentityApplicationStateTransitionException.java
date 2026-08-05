package com.campusguinness.identity.internal.domain;

public class InvalidStudentIdentityApplicationStateTransitionException extends RuntimeException {
    public InvalidStudentIdentityApplicationStateTransitionException(
            StudentIdentityApplicationStatus current, String action) {
        super("Cannot " + action + " student identity application from status " + current);
    }
}
