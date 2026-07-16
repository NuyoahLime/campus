package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolActivated(SchoolId schoolId, Instant occurredAt) {
    public SchoolActivated(SchoolId id) { this(id, Instant.now()); }
}
