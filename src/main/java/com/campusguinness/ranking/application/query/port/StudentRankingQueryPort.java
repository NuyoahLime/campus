package com.campusguinness.ranking.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.StudentCurrentRankingDetail;
import com.campusguinness.ranking.application.query.model.StudentOwnRanking;
import com.campusguinness.ranking.application.query.model.StudentRankingProjectItem;

import java.util.Optional;
import java.util.UUID;

public interface StudentRankingQueryPort {

    QueryPage<StudentRankingProjectItem> findRankingProjects(
            UUID actorId,
            String executionStatus,
            String rankingAvailability,
            String keyword,
            int page,
            int size);

    Optional<StudentCurrentRankingDetail> findAccessibleCurrentRanking(
            UUID actorId, UUID activityProjectId);

    Optional<StudentOwnRanking> findOwnCurrentRanking(
            UUID actorId, UUID activityProjectId);

    boolean existsAccessibleAssignment(UUID actorId, UUID activityProjectId);
}
