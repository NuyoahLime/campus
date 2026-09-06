package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record L3AuthorizationDetailResult(
        UUID id,
        UUID schoolId,
        String schoolName,
        UUID projectId,
        String projectName,
        UUID ruleVersionId,
        Integer ruleVersionNumber,
        String dataScope,
        boolean allowSchoolName,
        boolean allowStudentName,
        String status,
        Instant submittedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewComment,
        String rejectReason,
        Instant pausedAt,
        Instant withdrawnAt,
        String withdrawReason,
        Instant createdAt,
        Instant updatedAt) {
}
