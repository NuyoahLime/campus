package com.campusguinness.school.internal.domain;

import java.util.UUID;

public record SchoolId(UUID value) {
    public SchoolId {
        if (value == null) throw new IllegalArgumentException("id must not be null");
    }
}
