package com.campusguinness.interfaces.web.challengeproject;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.project.application.query.ChallengeProjectQueryService;
import com.campusguinness.project.application.service.ChallengeProjectApplicationService;
import com.campusguinness.project.internal.domain.ProjectStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
public class PublicChallengeProjectController {
    private final ChallengeProjectQueryService queryService;
    private final ChallengeProjectApplicationService appService;

    public PublicChallengeProjectController(ChallengeProjectQueryService queryService,
                                             ChallengeProjectApplicationService appService) {
        this.queryService = queryService;
        this.appService = appService;
    }

    @GetMapping("/challenge-projects")
    public ResponseEntity<PageResponse<PublicProjectItem>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listPublic(page, size);
        var items = result.items().stream()
                .map(r -> new PublicProjectItem(r.id(), r.name(), r.category(),
                        r.scoreStorageType(), r.comparisonDirection(), r.createdAt()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/challenge-projects/{projectId}")
    public ResponseEntity<PublicProjectDetail> getDetail(@PathVariable UUID projectId) {
        var project = appService.findById(projectId);
        if (project.status() != ProjectStatus.PUBLISHED) return ResponseEntity.notFound().build();
        var sc = project.scoreConfig();
        return ResponseEntity.ok(new PublicProjectDetail(
                project.id().value(), project.name().value(), project.category().value(),
                project.description(), sc.rulesText(),
                sc.storageType().name(), sc.indicatorType().name(), sc.comparisonDirection().name(),
                sc.effectiveScoreRule(), sc.allowTie(), sc.scoreUnit(), sc.decimalPlaces(), sc.gradeOrder()));
    }

    public record PublicProjectItem(UUID projectId, String name, String category,
            String scoreStorageType, String comparisonDirection, java.time.Instant createdAt) {}
    public record PublicProjectDetail(UUID projectId, String name, String category,
            String description, String rulesText,
            String scoreStorageType, String scoreIndicatorType, String comparisonDirection,
            String effectiveScoreRule, boolean allowTie, String scoreUnit, Integer decimalPlaces, String gradeOrder) {}
}
