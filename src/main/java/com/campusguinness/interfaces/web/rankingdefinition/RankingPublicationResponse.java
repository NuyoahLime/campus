package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.ranking.application.result.RankingPublicationResult;

import java.time.Instant;
import java.util.UUID;

public record RankingPublicationResponse(
        UUID rankingDefinitionId,
        UUID rankingVersionId,
        UUID previousCurrentVersionId,
        UUID currentVersionId,
        String status,
        Instant publishedAt
) {
    public static RankingPublicationResponse from(RankingPublicationResult result) {
        return new RankingPublicationResponse(
                result.rankingDefinitionId(),
                result.rankingVersionId(),
                result.previousCurrentVersionId(),
                result.currentVersionId(),
                result.status(),
                result.publishedAt());
    }
}
