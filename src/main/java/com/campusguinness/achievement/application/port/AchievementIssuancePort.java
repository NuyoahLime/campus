package com.campusguinness.achievement.application.port;

import com.campusguinness.achievement.application.query.model.AchievementIssueResult;

import java.util.Optional;
import java.util.UUID;

public interface AchievementIssuancePort {

    Optional<AchievementIssueResult> issueForSchool(
            UUID schoolId,
            UUID rankingEntryId,
            UUID issuedBy,
            String verificationCode);

    Optional<AchievementIssueResult> issueForActivityProject(
            UUID activityProjectId,
            UUID rankingEntryId,
            UUID issuedBy,
            String verificationCode);

    void revokeByRankingVersion(
            UUID rankingVersionId, UUID revokedBy, String reason);
}
