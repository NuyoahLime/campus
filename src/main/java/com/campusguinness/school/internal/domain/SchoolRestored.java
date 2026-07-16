package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolRestored(SchoolId schoolId, Instant occurredAt) {
    public SchoolRestored(SchoolId id) { this(id, Instant.now()); }
}
