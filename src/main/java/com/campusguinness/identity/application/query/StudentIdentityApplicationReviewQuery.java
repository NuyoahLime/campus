package com.campusguinness.identity.application.query;

import com.campusguinness.identity.internal.domain.StudentIdentityApplicationStatus;

import java.util.Optional;
import java.util.UUID;

public interface StudentIdentityApplicationReviewQuery {

    ReviewPageResult<StudentIdentityApplicationSummary> findBySchool(
            UUID schoolId,
            StudentIdentityApplicationStatus status,
            int page,
            int size
    );

    Optional<StudentIdentityApplicationDetail> findDetail(UUID schoolId, UUID applicationId);
}
