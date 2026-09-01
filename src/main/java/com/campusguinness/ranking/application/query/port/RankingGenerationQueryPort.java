package com.campusguinness.ranking.application.query.port;

import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RankingGenerationQueryPort {
    Optional<RankingGenerationContext> findContext(UUID activityProjectId);

    List<RankingGenerationSourceRow> findAuthoritativeEffectiveScores(UUID activityProjectId, UUID schoolId);
}
