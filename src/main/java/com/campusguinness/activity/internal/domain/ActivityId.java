package com.campusguinness.activity.internal.domain;

import java.util.UUID;

public record ActivityId(UUID value) {
    public ActivityId {
        if (value == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }
}
