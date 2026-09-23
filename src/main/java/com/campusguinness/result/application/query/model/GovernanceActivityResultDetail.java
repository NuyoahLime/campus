package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GovernanceActivityResultDetail(
        UUID resultId,
        UUID schoolId,
        UUID activityId,
        String schoolName,
        String activityTitle,
        String activityExecutionStatus,
        String internalStatus,
        String publicStatus,
        boolean publicVisibilityBlocked,
        UUID currentCandidateVersionId,
        UUID currentInternalVersionId,
        UUID currentPublicVersionId,
        ActivityResultVersionProjection currentPublicVersion,
        List<GovernanceActivityResultHistoryEntry> governanceHistory,
        Instant updatedAt) {
}
