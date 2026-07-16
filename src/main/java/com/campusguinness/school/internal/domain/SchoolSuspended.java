package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolSuspended(SchoolId schoolId, String reason, Instant occurredAt) {
    public SchoolSuspended(SchoolId id, String reason) { this(id, reason, Instant.now()); }
}
