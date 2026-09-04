package com.campusguinness.interfaces.web.rankingmanagement;

import com.campusguinness.ranking.application.query.model.RankingManagementDefinitionResult;
import com.campusguinness.ranking.application.query.model.RankingManagementEntryResult;
import com.campusguinness.ranking.application.query.model.RankingManagementVersionResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RankingManagementResponse(
        UUID id,
        String name,
        String layer,
        boolean enabled,
        UUID schoolId,
        String schoolName,
        UUID projectId,
        String projectName,
        UUID activityId,
        String activityTitle,
        UUID activityProjectId,
        String dimensionFilters,
        String selectionPolicy,
        String grade,
        String className,
        Instant activityPeriodStart,
        Instant activityPeriodEnd,
        Version latestGeneratedVersion,
        Version currentPublishedVersion
) {
    public static RankingManagementResponse from(RankingManagementDefinitionResult result) {
        return new RankingManagementResponse(
                result.id(),
                result.name(),
                result.layer(),
                result.enabled(),
                result.schoolId(),
                result.schoolName(),
                result.projectId(),
                result.projectName(),
                result.activityId(),
                result.activityTitle(),
                result.activityProjectId(),
                result.dimensionFilters(),
                result.selectionPolicy(),
                result.grade(),
                result.className(),
                result.activityPeriodStart(),
                result.activityPeriodEnd(),
                Version.from(result.latestGeneratedVersion()),
                Version.from(result.currentPublishedVersion()));
    }

    public record Version(
            UUID id,
            int versionNumber,
            String status,
            Instant generatedAt,
            Instant publishedAt,
            int entryCount,
            List<Entry> entries
    ) {
        static Version from(RankingManagementVersionResult result) {
            if (result == null) return null;
            return new Version(
                    result.id(),
                    result.versionNumber(),
                    result.status(),
                    result.generatedAt(),
                    result.publishedAt(),
                    result.entryCount(),
                    result.entries().stream().map(Entry::from).toList());
        }
    }

    public record Entry(
            int rankPosition,
            String studentDisplayName,
            String schoolName,
            String scoreDisplayValue
    ) {
        static Entry from(RankingManagementEntryResult result) {
            return new Entry(
                    result.rankPosition(),
                    result.studentDisplayName(),
                    result.schoolName(),
                    result.scoreDisplayValue());
        }
    }
}
