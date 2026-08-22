package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.ranking.application.query.model.RankingEntryReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadResult;
import com.campusguinness.ranking.application.query.model.RankingReadSummaryResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RankingReadResponse(
        UUID id,
        String name,
        String layer,
        UUID schoolId,
        String schoolName,
        UUID projectId,
        String projectName,
        int versionNumber,
        Instant publishedAt,
        List<RankingEntryResponse> entries
) {
    public static RankingReadResponse from(RankingReadResult result) {
        return new RankingReadResponse(result.id(), result.name(), result.layer(), result.schoolId(),
                result.schoolName(), result.projectId(), result.projectName(), result.versionNumber(),
                result.publishedAt(), result.entries().stream().map(RankingEntryResponse::from).toList());
    }

    public record RankingEntryResponse(
            int rankPosition,
            String studentDisplayName,
            String schoolName,
            String scoreDisplayValue
    ) {
        static RankingEntryResponse from(RankingEntryReadResult result) {
            return new RankingEntryResponse(result.rankPosition(), result.studentDisplayName(),
                    result.schoolName(), result.scoreDisplayValue());
        }
    }

    public record Summary(
            UUID id,
            String name,
            String layer,
            UUID schoolId,
            String schoolName,
            UUID projectId,
            String projectName,
            int versionNumber,
            Instant publishedAt
    ) {
        public static Summary from(RankingReadSummaryResult result) {
            return new Summary(result.id(), result.name(), result.layer(), result.schoolId(),
                    result.schoolName(), result.projectId(), result.projectName(),
                    result.versionNumber(), result.publishedAt());
        }
    }
}
