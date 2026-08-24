package com.campusguinness.activity.application.exception;

public final class ActivityParticipantAlreadyAssignedException extends RuntimeException {

    public ActivityParticipantAlreadyAssignedException() {
        super("Student is already assigned to this activity.");
    }
}
