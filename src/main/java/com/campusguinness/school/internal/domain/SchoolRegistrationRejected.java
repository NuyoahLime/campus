package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolRegistrationRejected(SchoolRegistrationId registrationId, Instant occurredAt) {
    public SchoolRegistrationRejected(SchoolRegistrationId id) { this(id, Instant.now()); }
}
