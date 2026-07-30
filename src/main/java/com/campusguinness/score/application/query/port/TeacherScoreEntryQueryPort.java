package com.campusguinness.score.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.model.TeacherScoreAttemptDetail;
import com.campusguinness.score.application.query.model.TeacherScoreAttemptItem;

import java.util.Optional;
import java.util.UUID;

public interface TeacherScoreEntryQueryPort {
    QueryPage<TeacherScoreAttemptItem> findMine(
            UUID actorId,
            String status,
            UUID activityProjectId,
            String keyword,
            int page,
            int size);

    Optional<TeacherScoreAttemptDetail> findDetail(
            UUID actorId,
            UUID attemptId);
}
