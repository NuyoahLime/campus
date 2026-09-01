package com.campusguinness.ranking.application.result;

import java.time.Instant;
import java.util.UUID;

public record RankingPublicationResult(
        UUID rankingDefinitionId,
        UUID rankingVersionId,
        UUID previousCurrentVersionId,
        UUID currentVersionId,
        String status,
        Instant publishedAt
) {}
