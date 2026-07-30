package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.exception.RankingNotFoundException;
import com.campusguinness.ranking.application.query.model.CalculatedRankingEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Compatibility facade for the legacy SUPER_ADMIN ranking paths.
 */
@Service
public class RankingPublicationService {

    private final SchoolAdminRankingApplicationService rankingService;

    public RankingPublicationService(
            SchoolAdminRankingApplicationService rankingService) {
        this.rankingService = rankingService;
    }

    public record PublicationResult(
            UUID versionId,
            UUID activityProjectId,
            int versionNumber,
            String direction,
            int totalRanked,
            List<CalculatedRankingEntry> entries) {
    }

    public record HistoryItem(
            UUID versionId,
            int versionNumber,
            String status,
            Instant publishedAt,
            UUID publishedBy,
            Instant withdrawnAt,
            UUID withdrawnBy,
            String withdrawalReason,
            int entryCount) {
    }

    @Transactional
    public PublicationResult publish(UUID activityProjectId, UUID publishedBy) {
        return toResult(rankingService.publishAsSuperAdmin(
                publishedBy, activityProjectId));
    }

    @Transactional(readOnly = true)
    public Optional<PublicationResult> getCurrent(UUID activityProjectId) {
        try {
            return Optional.of(toResult(
                    rankingService.getCurrentAsSuperAdmin(activityProjectId)));
        } catch (RankingNotFoundException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    public void withdraw(UUID activityProjectId, UUID withdrawnBy, String reason) {
        rankingService.withdrawAsSuperAdmin(
                withdrawnBy, activityProjectId, reason);
    }

    @Transactional(readOnly = true)
    public boolean hasCurrent(UUID activityProjectId) {
        return getCurrent(activityProjectId).isPresent();
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> getHistory(UUID activityProjectId) {
        return rankingService.getVersionsAsSuperAdmin(
                        activityProjectId, 0, 100)
                .items()
                .stream()
                .map(version -> new HistoryItem(
                        version.versionId(),
                        version.versionNumber(),
                        version.versionStatus().name(),
                        version.publishedAt(),
                        version.publishedBy(),
                        version.withdrawnAt(),
                        version.withdrawnBy(),
                        version.withdrawalReason(),
                        Math.toIntExact(version.entryCount())))
                .toList();
    }

    private static PublicationResult toResult(
            com.campusguinness.ranking.application.query.model.RankingVersionDetail version) {
        List<CalculatedRankingEntry> entries = version.entries().stream()
                .map(entry -> new CalculatedRankingEntry(
                        entry.rankPosition(),
                        entry.studentId(),
                        entry.studentDisplayName(),
                        entry.schoolName(),
                        entry.scoreAttemptId(),
                        entry.scoreDisplayValue(),
                        entry.scoreBusinessTime(),
                        version.currentRuleVersionId()))
                .toList();
        return new PublicationResult(
                version.versionId(),
                version.activityProjectId(),
                version.versionNumber(),
                version.comparisonDirection(),
                entries.size(),
                entries);
    }
}
