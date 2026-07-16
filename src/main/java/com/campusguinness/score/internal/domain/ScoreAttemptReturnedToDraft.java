package com.campusguinness.score.internal.domain;

import java.time.Instant;

/** Domain event: a rejected score attempt was returned to DRAFT for revision. */
public record ScoreAttemptReturnedToDraft(ScoreAttemptId attemptId, Instant occurredAt) {
    public ScoreAttemptReturnedToDraft(ScoreAttemptId id) { this(id, Instant.now()); }
}
