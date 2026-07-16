package com.campusguinness.activity.internal.domain;

import java.util.UUID;

public record ActivityApplicationId(UUID value) {
    public ActivityApplicationId {
        if (value == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }
}
