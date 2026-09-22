package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceActivityResultSummary(
        UUID resultId,
        UUID schoolId,
        UUID activityId,
        String schoolName,
        String activityTitle,
        String activityExecutionStatus,
        String internalStatus,
        String publicStatus,
        boolean publicVisibilityBlocked,
        UUID currentPublicVersionId,
        Instant updatedAt) {
}
