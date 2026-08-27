package com.campusguinness.score.internal.domain;

import java.time.Instant;

/** Domain event: a score attempt was accepted by review. Effective selection is separate. */
public record ScoreAttemptApproved(ScoreAttemptId attemptId, Instant occurredAt) {
    public ScoreAttemptApproved(ScoreAttemptId id) { this(id, Instant.now()); }
}
