package com.campusguinness.score.internal.domain;

import java.util.UUID;

public record ScoreAttemptId(UUID value) {
    public ScoreAttemptId {
        if (value == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }
}
