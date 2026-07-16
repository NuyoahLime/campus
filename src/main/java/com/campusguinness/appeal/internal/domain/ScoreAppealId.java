package com.campusguinness.appeal.internal.domain;
import java.util.UUID;
public record ScoreAppealId(UUID value) {
    public ScoreAppealId { if (value == null) throw new IllegalArgumentException("id must not be null"); }
}
