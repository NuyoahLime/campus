package com.campusguinness.appeal.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ScoreAppealListResult(
        UUID appealId,
        UUID scoreAttemptId,
        String activityName,
        String challengeProjectName,
        String appealType,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
