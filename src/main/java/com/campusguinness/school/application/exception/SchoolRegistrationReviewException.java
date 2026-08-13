package com.campusguinness.school.application.exception;

public final class SchoolRegistrationReviewException extends RuntimeException {
    private final String code;

    public SchoolRegistrationReviewException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
