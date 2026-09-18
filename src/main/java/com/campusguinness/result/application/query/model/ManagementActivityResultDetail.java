package com.campusguinness.result.application.query.model;

import java.util.UUID;

public record ManagementActivityResultDetail(
        UUID activityId,
        UUID resultId,
        String internalStatus,
        String publicStatus,
        UUID currentCandidateVersionId,
        UUID currentInternalVersionId,
        UUID currentPublicVersionId,
        boolean publicVisibilityBlocked,
        ActivityResultVersionProjection candidateProjection,
        ActivityResultVersionProjection internalProjection,
        ActivityResultVersionProjection publicProjection) {
}
