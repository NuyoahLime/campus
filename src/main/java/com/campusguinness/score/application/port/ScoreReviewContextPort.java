package com.campusguinness.score.application.port;

import java.util.Optional;
import java.util.UUID;

public interface ScoreReviewContextPort {
    Optional<ReviewContext> findReviewContext(UUID attemptId, UUID actorSchoolId);

    record ReviewContext(
            UUID attemptId,
            UUID schoolId,
            UUID activityProjectId,
            UUID studentId,
            String scoreStorageType,
            String effectiveScoreRule,
            String comparisonDirection,
            String gradeOrder) {
    }
}
