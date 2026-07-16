package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolReEnabled(SchoolId schoolId, Instant occurredAt) {
    public SchoolReEnabled(SchoolId id) { this(id, Instant.now()); }
}
