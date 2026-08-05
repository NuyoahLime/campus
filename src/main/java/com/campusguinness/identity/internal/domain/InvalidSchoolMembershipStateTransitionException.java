package com.campusguinness.identity.internal.domain;

/** Raised when a SchoolMembership lifecycle action is not valid for its current state. */
public class InvalidSchoolMembershipStateTransitionException extends RuntimeException {
    public InvalidSchoolMembershipStateTransitionException(MembershipStatus current, String action) {
        super("Cannot " + action + " school membership from status " + current);
    }
}
