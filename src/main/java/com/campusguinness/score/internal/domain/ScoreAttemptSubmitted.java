package com.campusguinness.score.internal.domain;

import java.time.Instant;

/** Domain event: a score attempt was submitted for review. */
public record ScoreAttemptSubmitted(ScoreAttemptId attemptId, Instant occurredAt) {
    public ScoreAttemptSubmitted(ScoreAttemptId id) { this(id, Instant.now()); }
}
