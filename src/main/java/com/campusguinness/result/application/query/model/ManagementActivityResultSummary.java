package com.campusguinness.result.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record ManagementActivityResultSummary(
        UUID activityId,
        String activityTitle,
        UUID resultId,
        String internalStatus,
        String publicStatus,
        boolean publicVisibilityBlocked,
        UUID currentCandidateVersionId,
        UUID currentInternalVersionId,
        UUID currentPublicVersionId,
        Instant updatedAt) {
}
