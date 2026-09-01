package com.campusguinness.ranking.application.port;

import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.application.service.GeneratedRankingSnapshot;
import com.campusguinness.ranking.application.service.RankingGenerationScope;
import com.campusguinness.ranking.internal.domain.RankingDefinition;

public interface RankingGenerationRepository {
    RankingGenerationResult saveGeneratedSnapshot(
            RankingDefinition definition,
            RankingGenerationScope scope,
            RankingGenerationContext context,
            GeneratedRankingSnapshot snapshot);
}
