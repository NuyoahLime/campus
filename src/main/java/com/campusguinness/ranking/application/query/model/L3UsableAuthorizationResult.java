package com.campusguinness.ranking.application.query.model;

import java.util.UUID;

public record L3UsableAuthorizationResult(
        UUID id,
        UUID schoolId,
        UUID projectId,
        UUID ruleVersionId,
        String dataScope,
        boolean allowSchoolName,
        boolean allowStudentName) {
}
