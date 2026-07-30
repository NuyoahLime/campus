package com.campusguinness.ranking.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingProjectDetail;
import com.campusguinness.ranking.application.query.model.RankingProjectItem;
import com.campusguinness.ranking.application.query.model.RankingVersionDetail;
import com.campusguinness.ranking.application.query.model.RankingVersionSummary;

import java.util.Optional;
import java.util.UUID;

public interface SchoolAdminRankingQueryPort {
    QueryPage<RankingProjectItem> findProjects(
            UUID schoolId,
            String executionStatus,
            String rankingStatus,
            String keyword,
            int page,
            int size);

    Optional<RankingProjectDetail> findProject(UUID schoolId, UUID activityProjectId);

    Optional<RankingVersionDetail> findCurrentVersion(UUID schoolId, UUID activityProjectId);

    QueryPage<RankingVersionSummary> findVersions(
            UUID schoolId, UUID activityProjectId, int page, int size);

    Optional<RankingVersionDetail> findVersion(UUID schoolId, UUID versionId);

    Optional<UUID> findSchoolId(UUID activityProjectId);
}
