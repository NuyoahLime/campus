package com.campusguinness.project.application.query.port;

import com.campusguinness.project.application.query.model.ChallengeProjectListResult;
import com.campusguinness.project.application.query.model.PublicProjectDetailResult;
import com.campusguinness.project.application.query.model.PublicProjectListFilter;
import com.campusguinness.project.application.query.model.QueryPage;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeProjectQueryPort {
    QueryPage<ChallengeProjectListResult> findPublished(int page, int size);

    QueryPage<ChallengeProjectListResult> findPublished(PublicProjectListFilter filter, int page, int size);

    Optional<PublicProjectDetailResult> findPublishedById(UUID projectId);
}
