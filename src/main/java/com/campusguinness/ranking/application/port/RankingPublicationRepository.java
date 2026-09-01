package com.campusguinness.ranking.application.port;

import com.campusguinness.ranking.application.result.RankingPublicationResult;
import com.campusguinness.ranking.internal.domain.RankingDefinition;

import java.util.UUID;

public interface RankingPublicationRepository {
    RankingPublicationResult publishGeneratedVersion(RankingDefinition definition, UUID rankingVersionId);
}
