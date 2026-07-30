package com.campusguinness.score.application.exception;

public class ScoreEntryNotFoundException extends RuntimeException {
    public ScoreEntryNotFoundException() {
        super("Score entry resource was not found");
    }
}
