package com.campusguinness.identity.internal.domain;

import java.util.UUID;

public record StudentIdentityApplicationId(UUID value) {
    public StudentIdentityApplicationId {
        if (value == null) {
            throw new IllegalArgumentException("student identity application id required");
        }
    }
}
