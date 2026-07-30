package com.campusguinness.score.application.exception;

public class ScoreReviewNotFoundException extends RuntimeException {
    public ScoreReviewNotFoundException() {
        super("ScoreAttempt not found");
    }
}
