package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.ranking.application.service.RankingPreviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RankingPreviewController {
    private final RankingPreviewService service;

    public RankingPreviewController(RankingPreviewService service) {
        this.service = service;
    }

    @GetMapping("/activity-projects/{activityProjectId}/ranking-preview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public RankingPreviewResponse preview(@PathVariable UUID activityProjectId) {
        var result = service.preview(activityProjectId);
        return new RankingPreviewResponse(result.activityProjectId(), result.direction(),
                result.totalRanked(), result.entries().stream()
                .map(e -> new RankEntry(e.rank(), e.studentId(), e.scoreAttemptId(), e.scoreDisplay()))
                .toList());
    }

    public record RankingPreviewResponse(UUID activityProjectId, String direction, int totalRanked,
                                          List<RankEntry> entries) {}
    public record RankEntry(int rank, UUID studentId, UUID scoreAttemptId, String scoreDisplay) {}
}
