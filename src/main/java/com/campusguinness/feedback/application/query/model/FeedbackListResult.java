package com.campusguinness.feedback.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record FeedbackListResult(
        UUID feedbackId,
        String feedbackType,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
