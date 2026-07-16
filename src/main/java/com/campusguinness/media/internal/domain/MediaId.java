package com.campusguinness.media.internal.domain;

import java.util.UUID;

public record MediaId(UUID value) {
    public MediaId {
        if (value == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }
}
