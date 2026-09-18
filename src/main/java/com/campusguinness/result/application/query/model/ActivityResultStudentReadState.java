package com.campusguinness.result.application.query.model;

import java.util.UUID;

public record ActivityResultStudentReadState(
        UUID activityId,
        UUID resultId,
        String activityExecutionStatus,
        String internalStatus,
        String publicStatus,
        UUID currentCandidateVersionId,
        UUID currentInternalVersionId,
        UUID currentPublicVersionId,
        boolean publicVisibilityBlocked,
        ActivityResultVersionProjection internalProjection,
        ActivityResultVersionProjection publicProjection) {
}
