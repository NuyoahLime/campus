package com.campusguinness.interfaces.web.studentscore;

import com.campusguinness.score.application.query.model.StudentScoreListResult;

import java.time.Instant;
import java.util.UUID;

public record StudentScoreResponse(
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
        String status) {
    static StudentScoreResponse from(StudentScoreListResult result) {
        return new StudentScoreResponse(result.scoreAttemptId(), result.activityProjectId(), result.activityId(),
                result.activityName(), result.challengeProjectName(), result.attemptNumber(),
                result.scoreStorageType(), result.scoreValue(), result.scoreUnit(), result.scoreBusinessTime(),
                result.status());
    }
}
