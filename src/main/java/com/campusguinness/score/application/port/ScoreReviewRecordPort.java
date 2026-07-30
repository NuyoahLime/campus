package com.campusguinness.score.application.port;

import java.time.Instant;
import java.util.UUID;

public interface ScoreReviewRecordPort {
    void append(ScoreReviewRecord record);

    record ScoreReviewRecord(
            UUID id,
            UUID scoreAttemptId,
            UUID reviewerId,
            String reviewResult,
            String reviewComment,
            String rejectReason,
            Instant reviewedAt) {
    }
}
