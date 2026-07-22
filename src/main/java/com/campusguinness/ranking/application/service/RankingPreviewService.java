package com.campusguinness.ranking.application.service;

import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.project.internal.domain.ComparisonDirection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RankingPreviewService {
    private final ActivityProjectPort projectPort;
    private final ScoreAttemptRepository scoreAttemptRepository;

    public RankingPreviewService(ActivityProjectPort projectPort,
                                  ScoreAttemptRepository scoreAttemptRepository) {
        this.projectPort = projectPort;
        this.scoreAttemptRepository = scoreAttemptRepository;
    }

    public record PreviewResult(UUID activityProjectId, String direction, int totalRanked,
                                 List<RankingCalculator.RankingEntry> entries) {}

    public PreviewResult preview(UUID activityProjectId) {
        // Verify ActivityProject exists
        projectPort.findById(activityProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ActivityProject not found: " + activityProjectId));

        // Load APPROVED scores for this project
        var approved = scoreAttemptRepository.findApprovedByActivityProjectId(activityProjectId);

        // Determine direction from the project's rule (via ChallengeProject)
        // For MVP, use HIGHER_BETTER as default; actual direction comes from the ScoreConfig
        ComparisonDirection direction = ComparisonDirection.HIGHER_BETTER;

        var entries = RankingCalculator.rank(approved, direction);
        return new PreviewResult(activityProjectId, direction.name(), entries.size(), entries);
    }
}
