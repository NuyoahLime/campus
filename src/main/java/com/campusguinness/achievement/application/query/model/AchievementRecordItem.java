package com.campusguinness.achievement.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record AchievementRecordItem(
        UUID recordId,
        String recordTitle,
        String schoolName,
        String activityTitle,
        String projectName,
        int rankingVersionNumber,
        int rankPosition,
        String scoreDisplayValue,
        String scoreStorageType,
        String verificationCode,
        AchievementStatus status,
        Instant issuedAt,
        Instant revokedAt) {
}
