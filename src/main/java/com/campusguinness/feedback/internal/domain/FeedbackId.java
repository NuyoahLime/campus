package com.campusguinness.feedback.internal.domain;
import java.util.UUID;
public record FeedbackId(UUID value) {
    public FeedbackId { if (value == null) throw new IllegalArgumentException("id must not be null"); }
}
