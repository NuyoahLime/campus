package com.campusguinness.score.application.query.port;

import com.campusguinness.score.application.query.model.StudentScoreItem;
import com.campusguinness.score.application.query.model.StudentScoreDetail;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface StudentScoreQueryPort {
    QueryPage<StudentScoreItem> findByStudentId(
            UUID studentId, String status, UUID activityId, UUID projectId, int page, int size);

    Optional<StudentScoreDetail> findByIdAndStudentId(UUID attemptId, UUID studentId);
}
