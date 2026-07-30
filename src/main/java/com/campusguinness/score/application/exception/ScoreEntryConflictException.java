package com.campusguinness.score.application.exception;

public class ScoreEntryConflictException extends RuntimeException {
    private final String errorCode;

    public ScoreEntryConflictException(String message) {
        this("CONFLICT", message);
    }

    public ScoreEntryConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
