package com.campusguinness.interfaces.web.studentfeedback;

import com.campusguinness.feedback.application.query.model.FeedbackDetailResult;
import com.campusguinness.feedback.application.query.model.FeedbackListResult;

import java.time.Instant;
import java.util.UUID;

public record StudentFeedbackResponse(
        UUID feedbackId,
        String feedbackType,
        String content,
        String status,
        String reply,
        String closeReason,
        Instant createdAt,
        Instant updatedAt) {
    public static StudentFeedbackResponse from(FeedbackListResult result) {
        return new StudentFeedbackResponse(result.feedbackId(), result.feedbackType(), null,
                result.status(), null, null, result.createdAt(), result.updatedAt());
    }

    public static StudentFeedbackResponse from(FeedbackDetailResult result) {
        return new StudentFeedbackResponse(result.feedbackId(), result.feedbackType(), result.content(),
                result.status(), result.reply(), result.closeReason(), result.createdAt(), result.updatedAt());
    }
}
