package com.campusguinness.interfaces.web.l3authorization;

import com.campusguinness.ranking.application.query.model.L3AuthorizationDetailResult;
import com.campusguinness.ranking.application.query.model.L3AuthorizationSummaryResult;

import java.time.Instant;
import java.util.UUID;

public record L3AuthorizationResponse(
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

    static L3AuthorizationResponse command(UUID id, String status) {
        return new L3AuthorizationResponse(id, null, null, null, null, null, null,
                null, false, false, status, null, null, null, null, null,
                null, null, null, null, null);
    }

    static L3AuthorizationResponse summary(L3AuthorizationSummaryResult r) {
        return new L3AuthorizationResponse(r.id(), r.schoolId(), r.schoolName(), r.projectId(), r.projectName(),
                r.ruleVersionId(), r.ruleVersionNumber(), null, r.allowSchoolName(), r.allowStudentName(),
                r.status(), r.submittedAt(), null, r.reviewedAt(), null, null,
                null, null, null, r.createdAt(), r.updatedAt());
    }

    static L3AuthorizationResponse detail(L3AuthorizationDetailResult r) {
        return new L3AuthorizationResponse(r.id(), r.schoolId(), r.schoolName(), r.projectId(), r.projectName(),
                r.ruleVersionId(), r.ruleVersionNumber(), r.dataScope(), r.allowSchoolName(), r.allowStudentName(),
                r.status(), r.submittedAt(), r.reviewedBy(), r.reviewedAt(), r.reviewComment(), r.rejectReason(),
                r.pausedAt(), r.withdrawnAt(), r.withdrawReason(), r.createdAt(), r.updatedAt());
    }
}
