package com.campusguinness.ranking.application.result;

import java.time.Instant;
import java.util.UUID;

public record RankingGenerationResult(
        UUID rankingDefinitionId,
        UUID rankingVersionId,
        int versionNumber,
        int entryCount,
        String status,
        Instant generatedAt
) {}
