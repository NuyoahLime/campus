package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolRegistrationSupplementRequested(SchoolRegistrationId registrationId, Instant occurredAt) {
    public SchoolRegistrationSupplementRequested(SchoolRegistrationId id) { this(id, Instant.now()); }
}
