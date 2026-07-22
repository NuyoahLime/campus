package com.campusguinness.interfaces.web.ranking;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.ranking.application.service.*;
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
    private final StudentRankingService studentRankingService;
    private final CurrentActor currentActor;

    public RankingController(RankingPreviewService previewService,
                              RankingPublicationService publicationService,
                              StudentRankingService studentRankingService,
                              CurrentActor currentActor) {
        this.previewService = previewService;
        this.publicationService = publicationService;
        this.studentRankingService = studentRankingService;
        this.currentActor = currentActor;
    }

    // ── Admin ──

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

    @PostMapping("/activity-projects/{activityProjectId}/ranking-withdraw")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<Void> withdraw(@PathVariable UUID activityProjectId,
                                          @RequestBody WithdrawRequest req) {
        publicationService.withdraw(activityProjectId, currentActor.requireUserId(), req.reason());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/activity-projects/{activityProjectId}/ranking-history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public List<RankingPublicationService.HistoryItem> getHistory(@PathVariable UUID activityProjectId) {
        return publicationService.getHistory(activityProjectId);
    }

    // ── Student ──

    @GetMapping("/student/activity-projects/{activityProjectId}/ranking")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentRankingResponse> studentGetRanking(@PathVariable UUID activityProjectId) {
        UUID studentId = currentActor.requireUserId();
        return studentRankingService.getCurrentRanking(activityProjectId, studentId)
                .map(r -> ResponseEntity.ok(new StudentRankingResponse(r.activityProjectId(), r.version(),
                        r.direction(), r.totalRanked(), r.entries().stream()
                        .map(e -> new StudentRankEntry(e.rank(), e.scoreValue(), e.isCurrentStudent())).toList())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/student/activity-projects/{activityProjectId}/ranking/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentOwnRankResponse> studentGetMyRank(@PathVariable UUID activityProjectId) {
        UUID studentId = currentActor.requireUserId();
        return studentRankingService.getMyRank(activityProjectId, studentId)
                .map(r -> ResponseEntity.ok(new StudentOwnRankResponse(r.activityProjectId(), r.version(),
                        r.direction(), r.totalRanked(), r.rank(), r.scoreValue())))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Public ──

    @GetMapping("/public/activity-projects/{activityProjectId}/ranking")
    public ResponseEntity<PublicRankingResponse> publicGetRanking(@PathVariable UUID activityProjectId) {
        return studentRankingService.getCurrentRanking(activityProjectId, null)
                .map(r -> ResponseEntity.ok(new PublicRankingResponse(r.activityProjectId(), r.version(),
                        r.direction(), r.totalRanked(), r.entries().stream()
                        .map(e -> new PublicRankEntry(e.rank(), e.scoreValue())).toList())))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs ──

    private RankingResponse toResponse(UUID apId, String dir, int total, List<? extends RankingCalculator.RankingEntry> entries) {
        return new RankingResponse(apId, dir, total, entries.stream()
                .map(e -> new RankEntry(e.rank(), e.studentId(), e.scoreAttemptId(), e.scoreDisplay())).toList());
    }

    public record RankingResponse(UUID activityProjectId, String direction, int totalRanked, List<RankEntry> entries) {}
    public record RankEntry(int rank, UUID studentId, UUID scoreAttemptId, String scoreDisplay) {}
    public record StudentRankingResponse(UUID activityProjectId, int version, String direction,
                                          int totalRanked, List<StudentRankEntry> entries) {}
    public record StudentRankEntry(int rank, String scoreValue, boolean isCurrentStudent) {}
    public record StudentOwnRankResponse(UUID activityProjectId, int version, String direction,
                                          int totalRanked, int rank, String scoreValue) {}
    public record PublicRankingResponse(UUID activityProjectId, int version, String direction,
                                         int totalRanked, List<PublicRankEntry> entries) {}
    public record PublicRankEntry(int rank, String scoreValue) {}
    public record WithdrawRequest(String reason) {}
}
