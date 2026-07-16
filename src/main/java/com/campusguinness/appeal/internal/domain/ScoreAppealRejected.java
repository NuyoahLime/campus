package com.campusguinness.appeal.internal.domain;
import java.time.Instant;
public record ScoreAppealRejected(ScoreAppealId appealId, Instant occurredAt) {
    public ScoreAppealRejected(ScoreAppealId id) { this(id, Instant.now()); }
}
