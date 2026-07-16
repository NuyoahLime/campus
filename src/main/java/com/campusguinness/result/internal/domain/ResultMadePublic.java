package com.campusguinness.result.internal.domain;

import java.time.Instant;

public record ResultMadePublic(ActivityResultId resultId, Instant occurredAt) {
    public ResultMadePublic(ActivityResultId id) { this(id, Instant.now()); }
}
