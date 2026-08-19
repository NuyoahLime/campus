package com.campusguinness.appeal.application.query.port;

import com.campusguinness.appeal.application.query.model.ScoreAppealDetailResult;
import com.campusguinness.appeal.application.query.model.ScoreAppealListResult;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface ScoreAppealQueryPort {
    QueryPage<ScoreAppealListResult> findByStudent(UUID studentId, UUID schoolId, int page, int size);

    Optional<ScoreAppealDetailResult> findByIdAndStudent(UUID appealId, UUID studentId, UUID schoolId);

    QueryPage<ScoreAppealListResult> findBySchool(UUID schoolId, int page, int size);

    Optional<ScoreAppealDetailResult> findByIdAndSchool(UUID appealId, UUID schoolId);
}
