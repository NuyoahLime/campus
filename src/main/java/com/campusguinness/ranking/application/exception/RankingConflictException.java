package com.campusguinness.ranking.application.exception;

public class RankingConflictException extends RuntimeException {
    private final String errorCode;

    public RankingConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
