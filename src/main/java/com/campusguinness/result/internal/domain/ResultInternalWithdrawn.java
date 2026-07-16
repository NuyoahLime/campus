package com.campusguinness.result.internal.domain;

import java.time.Instant;

public record ResultInternalWithdrawn(ActivityResultId resultId, Instant occurredAt) {
    public ResultInternalWithdrawn(ActivityResultId id) { this(id, Instant.now()); }
}
