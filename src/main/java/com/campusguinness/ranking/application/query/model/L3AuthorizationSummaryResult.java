package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record L3AuthorizationSummaryResult(
        UUID id,
        UUID schoolId,
        String schoolName,
        UUID projectId,
        String projectName,
        UUID ruleVersionId,
        Integer ruleVersionNumber,
        String status,
        boolean allowSchoolName,
        boolean allowStudentName,
        Instant submittedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt) {
}
