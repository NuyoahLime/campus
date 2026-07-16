package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolRegistrationWithdrawn(SchoolRegistrationId registrationId, Instant occurredAt) {
    public SchoolRegistrationWithdrawn(SchoolRegistrationId id) { this(id, Instant.now()); }
}
