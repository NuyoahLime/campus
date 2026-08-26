package com.campusguinness.score.application.query.port;

import com.campusguinness.score.application.query.model.ScoreReviewHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface ScoreReviewHistoryQueryPort {
    List<ScoreReviewHistoryEntry> findByScoreAttemptId(UUID scoreAttemptId);
}
