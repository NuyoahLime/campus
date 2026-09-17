package com.campusguinness.result.internal.domain;

import java.util.UUID;

public record ResultReviewRecordId(UUID value) {
    public ResultReviewRecordId {
        if (value == null) throw new IllegalArgumentException("ResultReviewRecordId required");
    }
}
