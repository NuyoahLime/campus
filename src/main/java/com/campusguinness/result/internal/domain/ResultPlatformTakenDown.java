package com.campusguinness.result.internal.domain;

import java.time.Instant;

public record ResultPlatformTakenDown(ActivityResultId resultId, Instant occurredAt) {
    public ResultPlatformTakenDown(ActivityResultId id) { this(id, Instant.now()); }
}
