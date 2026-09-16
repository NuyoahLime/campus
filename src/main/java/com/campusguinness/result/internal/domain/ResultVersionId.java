package com.campusguinness.result.internal.domain;

import java.util.UUID;

public record ResultVersionId(UUID value) {
    public ResultVersionId {
        if (value == null) throw new IllegalArgumentException("value required");
    }
}
