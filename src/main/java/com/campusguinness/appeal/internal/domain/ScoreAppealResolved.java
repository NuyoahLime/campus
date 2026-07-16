package com.campusguinness.appeal.internal.domain;
import java.time.Instant;
public record ScoreAppealResolved(ScoreAppealId appealId, Instant occurredAt) {
    public ScoreAppealResolved(ScoreAppealId id) { this(id, Instant.now()); }
}
