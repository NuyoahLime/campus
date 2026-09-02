package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RankingManagementVersionResult(
        UUID id,
        int versionNumber,
        String status,
        Instant generatedAt,
        Instant publishedAt,
        int entryCount,
        List<RankingManagementEntryResult> entries
) {}
