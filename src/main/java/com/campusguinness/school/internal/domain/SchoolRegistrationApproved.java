package com.campusguinness.school.internal.domain;

import java.time.Instant;

public record SchoolRegistrationApproved(SchoolRegistrationId registrationId, Instant occurredAt) {
    public SchoolRegistrationApproved(SchoolRegistrationId id) { this(id, Instant.now()); }
}
