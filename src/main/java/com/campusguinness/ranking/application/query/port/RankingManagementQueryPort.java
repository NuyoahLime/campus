package com.campusguinness.ranking.application.query.port;

import com.campusguinness.project.application.query.model.QueryPage;
import com.campusguinness.ranking.application.query.model.RankingManagementDefinitionResult;

import java.util.Optional;
import java.util.UUID;

public interface RankingManagementQueryPort {
    QueryPage<RankingManagementDefinitionResult> list(UUID schoolId, int page, int size);

    Optional<RankingManagementDefinitionResult> detail(UUID definitionId, UUID schoolId);
}
