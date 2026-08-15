package com.campusguinness.school.internal.persistence;

public final class SchoolRegistrationConcurrentReviewException extends RuntimeException {
    public SchoolRegistrationConcurrentReviewException() {
        super("School registration was updated by another reviewer.");
    }
}
