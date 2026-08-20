package com.campusguinness.interfaces.web.studentappeal;

import com.campusguinness.appeal.application.query.model.ScoreAppealDetailResult;
import com.campusguinness.appeal.application.query.model.ScoreAppealListResult;

import java.time.Instant;
import java.util.UUID;

public record StudentScoreAppealResponse(
        UUID appealId,
        UUID scoreAttemptId,
        String activityName,
        String challengeProjectName,
        String scoreStorageType,
        String scoreValue,
        String scoreUnit,
        String appealType,
        String appealReason,
        String status,
        String resolution,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {
    public static StudentScoreAppealResponse from(ScoreAppealListResult result) {
        return new StudentScoreAppealResponse(result.appealId(), result.scoreAttemptId(),
                result.activityName(), result.challengeProjectName(), null, null, null,
                result.appealType(), null, result.status(), null, null, result.createdAt(), result.updatedAt());
    }

    public static StudentScoreAppealResponse from(ScoreAppealDetailResult result) {
        return new StudentScoreAppealResponse(result.appealId(), result.scoreAttemptId(),
                result.activityName(), result.challengeProjectName(), result.scoreStorageType(), result.scoreValue(),
                result.scoreUnit(), result.appealType(), result.appealReason(), result.status(), result.resolution(),
                result.resolvedAt(), result.createdAt(), result.updatedAt());
    }
}
