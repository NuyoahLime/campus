package com.campusguinness.score.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.score.application.query.model.ScoreEntryParticipantOption;
import com.campusguinness.score.application.query.model.ScoreEntryProjectOption;

import java.util.UUID;

public interface SchoolAdminScoreEntryQueryPort {
    QueryPage<ScoreEntryProjectOption> findProjectOptions(
            UUID schoolId, String keyword, int page, int size);

    QueryPage<ScoreEntryParticipantOption> findParticipantOptions(
            UUID schoolId,
            UUID activityProjectId,
            String keyword,
            int page,
            int size);
}
