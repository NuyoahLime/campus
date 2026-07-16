package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolDisabled(SchoolId schoolId, String reason, Instant occurredAt) {
    public SchoolDisabled(SchoolId id, String reason) { this(id, reason, Instant.now()); }
}
