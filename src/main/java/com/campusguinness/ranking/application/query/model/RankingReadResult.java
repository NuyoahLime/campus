package com.campusguinness.ranking.application.query.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RankingReadResult(
        UUID id,
        String name,
        String layer,
        UUID schoolId,
        String schoolName,
        UUID projectId,
        String projectName,
        int versionNumber,
        Instant publishedAt,
        List<RankingEntryReadResult> entries
) {}
