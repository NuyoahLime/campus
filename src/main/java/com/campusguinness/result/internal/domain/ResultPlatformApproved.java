package com.campusguinness.result.internal.domain;

import java.time.Instant;

public record ResultPlatformApproved(ActivityResultId resultId, Instant occurredAt) {
    public ResultPlatformApproved(ActivityResultId id) { this(id, Instant.now()); }
}
