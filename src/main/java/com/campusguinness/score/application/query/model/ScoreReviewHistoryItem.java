package com.campusguinness.score.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ScoreReviewHistoryItem(
        UUID reviewRecordId,
        UUID reviewerId,
        String reviewerName,
        String reviewResult,
        String reviewComment,
        String rejectReason,
        Instant reviewedAt) {
}
