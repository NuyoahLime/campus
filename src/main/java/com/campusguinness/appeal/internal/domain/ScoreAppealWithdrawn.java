package com.campusguinness.appeal.internal.domain;
import java.time.Instant;
public record ScoreAppealWithdrawn(ScoreAppealId appealId, Instant occurredAt) {
    public ScoreAppealWithdrawn(ScoreAppealId id) { this(id, Instant.now()); }
}
