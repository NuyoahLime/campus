package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolRegistrationSubmitted(SchoolRegistrationId registrationId, Instant occurredAt) {
    public SchoolRegistrationSubmitted(SchoolRegistrationId id) { this(id, Instant.now()); }
}
