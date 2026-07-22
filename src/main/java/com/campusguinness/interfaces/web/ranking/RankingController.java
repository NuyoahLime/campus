package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.ranking.application.service.RankingPreviewService;
import com.campusguinness.ranking.application.service.RankingCalculator;
import com.campusguinness.ranking.application.service.RankingPublicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RankingController {
    private final RankingPreviewService previewService;
    private final RankingPublicationService publicationService;
    private final CurrentActor currentActor;

    public RankingController(RankingPreviewService previewService,
                              RankingPublicationService publicationService,
                              CurrentActor currentActor) {
        this.previewService = previewService;
        this.publicationService = publicationService;
        this.currentActor = currentActor;
    }

    @GetMapping("/activity-projects/{activityProjectId}/ranking-preview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public RankingResponse preview(@PathVariable UUID activityProjectId) {
        var r = previewService.preview(activityProjectId);
        return toResponse(r.activityProjectId(), r.direction(), r.totalRanked(), r.entries());
    }

    @PostMapping("/activity-projects/{activityProjectId}/ranking-publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public RankingResponse publish(@PathVariable UUID activityProjectId) {
        var r = publicationService.publish(activityProjectId, currentActor.requireUserId());
        return toResponse(r.activityProjectId(), r.direction(), r.totalRanked(), r.entries());
    }

    @GetMapping("/activity-projects/{activityProjectId}/ranking-current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<RankingResponse> getCurrent(@PathVariable UUID activityProjectId) {
        return publicationService.getCurrent(activityProjectId)
                .map(r -> ResponseEntity.ok(toResponse(r.activityProjectId(), r.direction(), r.totalRanked(), r.entries())))
                .orElse(ResponseEntity.notFound().build());
    }

    private RankingResponse toResponse(UUID apId, String dir, int total, List<? extends RankingCalculator.RankingEntry> entries) {
        return new RankingResponse(apId, dir, total, entries.stream()
                .map(e -> new RankEntry(e.rank(), e.studentId(), e.scoreAttemptId(), e.scoreDisplay())).toList());
    }

    public record RankingResponse(UUID activityProjectId, String direction, int totalRanked, List<RankEntry> entries) {}
    public record RankEntry(int rank, UUID studentId, UUID scoreAttemptId, String scoreDisplay) {}
}
