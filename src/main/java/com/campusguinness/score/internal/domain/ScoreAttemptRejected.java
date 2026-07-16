package com.campusguinness.score.internal.domain;

import java.time.Instant;

/** Domain event: a score attempt was rejected during review. */
public record ScoreAttemptRejected(ScoreAttemptId attemptId, String rejectReason, Instant occurredAt) {
    public ScoreAttemptRejected(ScoreAttemptId id, String rejectReason) {
        this(id, rejectReason, Instant.now());
    }
}
