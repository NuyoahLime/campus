package com.campusguinness.result.internal.domain;

import java.util.UUID;

public record ActivityResultId(UUID value) {
    public ActivityResultId {
        if (value == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }
}
