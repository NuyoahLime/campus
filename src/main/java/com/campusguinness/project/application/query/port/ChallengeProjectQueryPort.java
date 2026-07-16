package com.campusguinness.project.application.query.port;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.QueryPage;

public interface ChallengeProjectQueryPort {
    QueryPage<ChallengeProjectListResult> findPublished(int page, int size);
}
