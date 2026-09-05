package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.internal.domain.*;
import java.time.Instant;

final class L3AuthorizationPersistenceMapper {
    private L3AuthorizationPersistenceMapper() {}

    static L3AuthorizationEntity toEntity(L3Authorization domain) {
        var e = new L3AuthorizationEntity();
        e.setId(domain.id().value());
        e.setCreatedAt(Instant.now());
        copyToEntity(domain, e);
        return e;
    }

    static void copyToEntity(L3Authorization domain, L3AuthorizationEntity e) {
        e.setSchoolId(domain.schoolId());
        e.setProjectId(domain.projectId()); e.setRuleVersionId(domain.ruleVersionId());
        e.setDataScope(domain.dataScope()); e.setAllowSchoolName(domain.allowSchoolName());
        e.setAllowStudentName(domain.allowStudentName());
        e.setAuthorizationStatus(domain.status().name());
        e.setSubmittedAt(domain.submittedAt()); e.setReviewedBy(domain.reviewedBy());
        e.setReviewedAt(domain.reviewedAt()); e.setReviewComment(domain.reviewComment());
        e.setRejectReason(domain.rejectReason()); e.setPausedAt(domain.pausedAt());
        e.setWithdrawnAt(domain.withdrawnAt()); e.setWithdrawReason(domain.withdrawReason());
        e.setUpdatedAt(Instant.now());
    }

    static L3Authorization toDomain(L3AuthorizationEntity e) {
        return L3Authorization.reconstitute(new L3Authorization.Builder()
                .id(new L3AuthorizationId(e.getId())).schoolId(e.getSchoolId())
                .projectId(e.getProjectId()).ruleVersionId(e.getRuleVersionId())
                .dataScope(e.getDataScope()).allowSchoolName(e.isAllowSchoolName())
                .allowStudentName(e.isAllowStudentName()),
                AuthorizationStatus.valueOf(e.getAuthorizationStatus()),
                e.getSubmittedAt(), e.getReviewedBy(), e.getReviewedAt(),
                e.getReviewComment(), e.getRejectReason(),
                e.getPausedAt(), e.getWithdrawnAt(), e.getWithdrawReason());
    }
}
