package com.campusguinness.result.internal.domain;

import java.time.Instant;

public record ResultInternalPublished(ActivityResultId resultId, Instant occurredAt) {
    public ResultInternalPublished(ActivityResultId id) { this(id, Instant.now()); }
}
