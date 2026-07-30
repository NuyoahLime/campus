package com.campusguinness.ranking.application.service;

import com.campusguinness.ranking.application.query.model.CalculatedRankingEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Compatibility facade for the legacy SUPER_ADMIN ranking path.
 */
@Service
@Transactional(readOnly = true)
public class RankingPreviewService {

    private final SchoolAdminRankingApplicationService rankingService;

    public RankingPreviewService(
            SchoolAdminRankingApplicationService rankingService) {
        this.rankingService = rankingService;
    }

    public record PreviewResult(
            UUID activityProjectId,
            String direction,
            int totalRanked,
            List<CalculatedRankingEntry> entries) {
    }

    public PreviewResult preview(UUID activityProjectId) {
        var preview = rankingService.previewAsSuperAdmin(activityProjectId);
        return new PreviewResult(
                preview.activityProjectId(),
                preview.comparisonDirection(),
                preview.totalRanked(),
                preview.entries());
    }
}
