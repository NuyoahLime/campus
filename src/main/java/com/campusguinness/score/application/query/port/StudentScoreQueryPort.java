package com.campusguinness.score.application.query.port;

import com.campusguinness.score.application.query.model.StudentScoreDetailResult;
import com.campusguinness.score.application.query.model.StudentScoreListResult;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface StudentScoreQueryPort {
    QueryPage<StudentScoreListResult> findVisibleByStudent(UUID studentId, UUID schoolId, int page, int size);

    Optional<StudentScoreDetailResult> findVisibleById(UUID scoreAttemptId, UUID studentId, UUID schoolId);
}
