package com.campusguinness.achievement.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminAchievementDetail(
        UUID recordId,
        UUID activityProjectId,
        UUID rankingVersionId,
        int rankingVersionNumber,
        UUID rankingEntryId,
        UUID studentId,
        String studentDisplayName,
        String schoolName,
        String activityTitle,
        String projectName,
        int rankPosition,
        String scoreDisplayValue,
        String scoreStorageType,
        String recordTitle,
        String verificationCode,
        AchievementStatus status,
        Instant issuedAt,
        UUID issuedBy,
        String issuedByName,
        Instant revokedAt,
        UUID revokedBy,
        String revocationReason,
        boolean created) {

    public SchoolAdminAchievementDetail withCreated(boolean value) {
        return new SchoolAdminAchievementDetail(
                recordId,
                activityProjectId,
                rankingVersionId,
                rankingVersionNumber,
                rankingEntryId,
                studentId,
                studentDisplayName,
                schoolName,
                activityTitle,
                projectName,
                rankPosition,
                scoreDisplayValue,
                scoreStorageType,
                recordTitle,
                verificationCode,
                status,
                issuedAt,
                issuedBy,
                issuedByName,
                revokedAt,
                revokedBy,
                revocationReason,
                value);
    }
}
