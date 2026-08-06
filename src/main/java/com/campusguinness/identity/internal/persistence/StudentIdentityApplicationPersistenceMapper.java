package com.campusguinness.identity.internal.persistence;

import com.campusguinness.identity.internal.domain.StudentIdentityApplication;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationId;
import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;

import java.time.Instant;

final class StudentIdentityApplicationPersistenceMapper {
    private StudentIdentityApplicationPersistenceMapper() {}

    static StudentIdentityApplicationEntity toEntity(StudentIdentityApplication domain) {
        var e = new StudentIdentityApplicationEntity();
        e.setId(domain.id().value());
        e.setUserId(domain.userId());
        e.setSchoolId(domain.schoolId());
        e.setRealName(domain.realName());
        e.setStudentNumber(domain.studentNumber());
        e.setGrade(domain.grade());
        e.setClassName(domain.className());
        e.setEvidenceFileKey(domain.evidenceFileKey());
        e.setApplicationStatus(domain.status().name());
        e.setReviewedBy(domain.reviewedBy());
        e.setReviewedAt(domain.reviewedAt());
        e.setRejectionReason(domain.rejectionReason());
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    static void updateEntity(StudentIdentityApplicationEntity e, StudentIdentityApplication domain) {
        e.setUserId(domain.userId());
        e.setSchoolId(domain.schoolId());
        e.setRealName(domain.realName());
        e.setStudentNumber(domain.studentNumber());
        e.setGrade(domain.grade());
        e.setClassName(domain.className());
        e.setEvidenceFileKey(domain.evidenceFileKey());
        e.setApplicationStatus(domain.status().name());
        e.setReviewedBy(domain.reviewedBy());
        e.setReviewedAt(domain.reviewedAt());
        e.setRejectionReason(domain.rejectionReason());
        e.setUpdatedAt(Instant.now());
    }

    static StudentIdentityApplication toDomain(StudentIdentityApplicationEntity e) {
        return StudentIdentityApplication.reconstitute(new StudentIdentityApplication.Builder()
                .id(new StudentIdentityApplicationId(e.getId()))
                .userId(e.getUserId())
                .schoolId(e.getSchoolId())
                .realName(e.getRealName())
                .studentNumber(e.getStudentNumber())
                .grade(e.getGrade())
                .className(e.getClassName())
                .evidenceFileKey(e.getEvidenceFileKey())
                .status(StudentIdentityApplicationStatus.valueOf(e.getApplicationStatus()))
                .reviewedBy(e.getReviewedBy())
                .reviewedAt(e.getReviewedAt())
                .rejectionReason(e.getRejectionReason()));
    }
}
