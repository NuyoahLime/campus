package com.campusguinness.result.internal.domain;

import java.time.Instant;

public record ResultSubmittedForReview(ActivityResultId resultId, Instant occurredAt) {
    public ResultSubmittedForReview(ActivityResultId id) { this(id, Instant.now()); }
}
