package com.campusguinness.score.internal.persistence;

/** Infrastructure exception: entity contains corrupted or inconsistent score data. */
public class ScoreValuePersistenceException extends RuntimeException {
    public ScoreValuePersistenceException(String message) { super(message); }
    public ScoreValuePersistenceException(String message, Throwable cause) { super(message, cause); }
}
