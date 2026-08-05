package com.campusguinness.identity.internal.domain;

public class InvalidSchoolAdminInvitationStateTransitionException extends RuntimeException {
    public InvalidSchoolAdminInvitationStateTransitionException(SchoolAdminInvitationStatus current, String action) {
        super("Cannot " + action + " school admin invitation from status " + current);
    }
}
