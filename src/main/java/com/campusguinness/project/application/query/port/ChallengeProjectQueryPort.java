package com.campusguinness.project.application.query.port;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.ChallengeProjectDetailResult;
import com.campusguinness.project.application.query.model.ChallengeProjectGovernanceListResult;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeProjectQueryPort {
    default QueryPage<ChallengeProjectListResult> findPublished(int page, int size) {
        return findPublished(page, size, null, null);
    }

    QueryPage<ChallengeProjectListResult> findPublished(int page, int size, String category, String query);

    Optional<ChallengeProjectDetailResult> findPublishedById(UUID id);

    QueryPage<ChallengeProjectGovernanceListResult> findGovernance(
            int page, int size, String status, String category, String query);

    Optional<ChallengeProjectDetailResult> findGovernanceById(UUID id);
}
