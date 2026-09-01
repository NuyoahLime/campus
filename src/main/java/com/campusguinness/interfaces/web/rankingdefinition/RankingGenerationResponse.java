package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.ranking.application.result.RankingGenerationResult;

import java.time.Instant;
import java.util.UUID;

public record RankingGenerationResponse(
        UUID rankingDefinitionId,
        UUID rankingVersionId,
        int versionNumber,
        int entryCount,
        String status,
        Instant generatedAt
) {
    public static RankingGenerationResponse from(RankingGenerationResult result) {
        return new RankingGenerationResponse(
                result.rankingDefinitionId(),
                result.rankingVersionId(),
                result.versionNumber(),
                result.entryCount(),
                result.status(),
                result.generatedAt());
    }
}
