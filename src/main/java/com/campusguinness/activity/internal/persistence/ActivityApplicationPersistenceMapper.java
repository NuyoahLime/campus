package com.campusguinness.activity.internal.persistence;

import com.campusguinness.activity.internal.domain.*;
import java.time.Instant;

final class ActivityApplicationPersistenceMapper {
    private ActivityApplicationPersistenceMapper() {}

    static ActivityApplicationEntity toEntity(ActivityApplication domain) {
        var e = new ActivityApplicationEntity();
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setApplicantId(domain.applicantId()); e.setTitle(domain.title());
        e.setDescription(domain.description());
        e.setApplicationStatus(domain.status().name());
        e.setCreatedActivityId(domain.createdActivityId());
        e.setReviewedBy(domain.reviewedBy()); e.setReviewedAt(domain.reviewedAt());
        e.setReviewComment(domain.reviewComment()); e.setRejectReason(domain.rejectReason());
        e.setApplicationVersion(domain.applicationVersion());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    static ActivityApplication toDomain(ActivityApplicationEntity e) {
        return ActivityApplication.reconstitute(new ActivityApplication.Builder()
                .id(new ActivityApplicationId(e.getId())).schoolId(e.getSchoolId())
                .applicantId(e.getApplicantId()).title(e.getTitle()).description(e.getDescription())
                .status(ApplicationStatus.valueOf(e.getApplicationStatus()))
                .applicationVersion(e.getApplicationVersion())
                .createdActivityId(e.getCreatedActivityId())
                .reviewedBy(e.getReviewedBy()).reviewedAt(e.getReviewedAt())
                .reviewComment(e.getReviewComment()).rejectReason(e.getRejectReason()));
    }
}
