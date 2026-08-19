package com.campusguinness.feedback.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record FeedbackDetailResult(
        UUID feedbackId,
        String feedbackType,
        String content,
        String status,
        String reply,
        String closeReason,
        Instant createdAt,
        Instant updatedAt) {
}
