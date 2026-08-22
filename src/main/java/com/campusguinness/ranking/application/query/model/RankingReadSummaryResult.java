package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.UUID;

public record RankingReadSummaryResult(
        UUID id,
        String name,
        String layer,
        UUID schoolId,
        String schoolName,
        UUID projectId,
        String projectName,
        int versionNumber,
        Instant publishedAt
) {}
