package com.campusguinness.activity.application.query.port;

import com.campusguinness.activity.application.query.model.ActivityListResult;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityQueryPort {
    QueryPage<ActivityListResult> findPublic(int page, int size, List<String> executionStatuses);
    QueryPage<ActivityListResult> findPublicPublished(int page, int size, List<String> executionStatuses);

    /** School-scoped query: own school only. */
    QueryPage<ActivityListResult> findBySchool(UUID schoolId, String executionStatus,
            String publicStatus, String keyword, int page, int size);

    /** Detail with schoolId check: ensures cross-school isolation. */
    Optional<ActivityListResult> findByIdAndSchoolId(UUID activityId, UUID schoolId);

    /** Public review queue: default PENDING_PLATFORM_REVIEW. */
    QueryPage<ActivityListResult> findPublicReview(String schoolId, String publicStatus,
            int page, int size);
}
