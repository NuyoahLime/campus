package com.campusguinness.score.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptDetail;
import com.campusguinness.score.application.query.model.SchoolAdminScoreAttemptItem;

import java.util.Optional;
import java.util.UUID;

public interface SchoolAdminScoreQueryPort {
    QueryPage<SchoolAdminScoreAttemptItem> findBySchool(
            UUID schoolId,
            String status,
            UUID activityId,
            UUID projectId,
            String keyword,
            int page,
            int size);

    Optional<SchoolAdminScoreAttemptDetail> findDetail(UUID schoolId, UUID attemptId);
}
