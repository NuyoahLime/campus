package com.campusguinness.score.application.port;

import java.util.UUID;

public interface ScoreReviewRecordPort {
    void append(UUID scoreAttemptId, UUID reviewerId, String result, String reason);
}
