package com.campusguinness.ranking.application.service;

import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
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
    private final ActivityRepository activityRepository;

    public RankingPreviewService(ActivityProjectPort projectPort,
                                  ScoreAttemptRepository scoreAttemptRepository,
                                  ActivityRepository activityRepository) {
        this.projectPort = projectPort;
        this.scoreAttemptRepository = scoreAttemptRepository;
        this.activityRepository = activityRepository;
    }

    public record PreviewResult(UUID activityProjectId, String direction, int totalRanked,
                                 List<RankingCalculator.RankingEntry> entries) {}

    public PreviewResult preview(UUID activityProjectId) {
        var ap = projectPort.findById(activityProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ActivityProject not found: " + activityProjectId));

        var activity = activityRepository.findById(new ActivityId(ap.activityId()))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));

        var status = activity.executionStatus();
        if (status != ExecutionStatus.IN_PROGRESS && status != ExecutionStatus.ENDED) {
            throw new IllegalStateException("Ranking preview not allowed when activity is " + status);
        }

        var approved = scoreAttemptRepository.findApprovedByActivityProjectId(activityProjectId);
        ComparisonDirection direction = ComparisonDirection.HIGHER_BETTER;
        var entries = RankingCalculator.rank(approved, direction);
        return new PreviewResult(activityProjectId, direction.name(), entries.size(), entries);
    }
}
