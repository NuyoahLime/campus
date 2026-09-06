package com.campusguinness.ranking.application.exception;

public class RankingGenerationException extends RuntimeException {
    private final String code;

    public RankingGenerationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
