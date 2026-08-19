package com.campusguinness.interfaces.web.studentscore;

import com.campusguinness.score.application.query.model.StudentScoreDetailResult;

import java.time.Instant;
import java.util.UUID;

public record StudentScoreDetailResponse(
        UUID scoreAttemptId,
        UUID activityProjectId,
        UUID activityId,
        String activityName,
        String challengeProjectName,
        int attemptNumber,
        String scoreStorageType,
        String scoreValue,
        String scoreUnit,
        Instant scoreBusinessTime,
        String status,
        UUID ruleVersionId,
        int ruleVersionNumber,
        String rulesText) {
    static StudentScoreDetailResponse from(StudentScoreDetailResult result) {
        return new StudentScoreDetailResponse(result.scoreAttemptId(), result.activityProjectId(), result.activityId(),
                result.activityName(), result.challengeProjectName(), result.attemptNumber(),
                result.scoreStorageType(), result.scoreValue(), result.scoreUnit(), result.scoreBusinessTime(),
                result.status(), result.ruleVersionId(), result.ruleVersionNumber(), result.rulesText());
    }
}
