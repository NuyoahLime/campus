package com.campusguinness.score.internal.domain;

import java.time.Instant;

/** Domain event: a score attempt was approved and became the current effective score. */
public record ScoreAttemptApproved(ScoreAttemptId attemptId, Instant occurredAt) {
    public ScoreAttemptApproved(ScoreAttemptId id) { this(id, Instant.now()); }
}
