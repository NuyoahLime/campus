package com.campusguinness.score.internal.domain;

import java.time.Instant;
import java.util.UUID;

/** Domain event: an approved score was invalidated by a correction (replaced by a new attempt). */
public record ScoreAttemptInvalidated(ScoreAttemptId attemptId, UUID replacedById, Instant occurredAt) {
    public ScoreAttemptInvalidated(ScoreAttemptId id, UUID replacedById) {
        this(id, replacedById, Instant.now());
    }
}
