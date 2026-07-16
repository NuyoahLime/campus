package com.campusguinness.school.internal.domain;

import java.util.UUID;

public record SchoolRegistrationId(UUID value) {
    public SchoolRegistrationId {
        if (value == null) throw new IllegalArgumentException("id must not be null");
    }
}
