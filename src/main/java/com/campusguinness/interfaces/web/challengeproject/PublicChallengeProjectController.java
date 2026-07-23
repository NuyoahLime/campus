package com.campusguinness.interfaces.web.challengeproject;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.project.application.query.ChallengeProjectQueryService;
import com.campusguinness.project.application.query.model.PublicProjectListFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
public class PublicChallengeProjectController {
    private final ChallengeProjectQueryService queryService;

    public PublicChallengeProjectController(ChallengeProjectQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/challenge-projects")
    public ResponseEntity<PageResponse<PublicProjectItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String scoreStorageType,
            @RequestParam(required = false) String venueKeyword,
            @RequestParam(required = false) String equipmentKeyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var filter = new PublicProjectListFilter(keyword, category, scoreStorageType,
                venueKeyword, equipmentKeyword);
        var result = queryService.listPublic(filter, page, size);
        var items = result.items().stream()
                .map(r -> new PublicProjectItem(r.projectId(), r.name(), r.category(),
                        r.descriptionSummary(), r.scoreStorageType(), r.comparisonDirection(),
                        r.scoreUnit()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/challenge-projects/{projectId}")
    public ResponseEntity<PublicProjectDetail> getDetail(@PathVariable UUID projectId) {
        return queryService.findPublishedById(projectId)
                .map(r -> ResponseEntity.ok(new PublicProjectDetail(
                        r.projectId(), r.name(), r.category(),
                        r.description(), r.venueRequirements(), r.equipmentRequirements(),
                        r.rulesText(), r.scoreStorageType(), r.scoreIndicatorType(),
                        r.comparisonDirection(), r.effectiveScoreRule(), r.allowTie(),
                        r.scoreUnit(), r.decimalPlaces(), r.gradeOrder())))
                .orElse(ResponseEntity.notFound().build());
    }

    public record PublicProjectItem(UUID projectId, String name, String category,
            String descriptionSummary, String scoreStorageType, String comparisonDirection,
            String scoreUnit) {}

    public record PublicProjectDetail(UUID projectId, String name, String category,
            String description, String venueRequirements, String equipmentRequirements,
            String rulesText, String scoreStorageType, String scoreIndicatorType,
            String comparisonDirection, String effectiveScoreRule, boolean allowTie,
            String scoreUnit, Integer decimalPlaces, String gradeOrder) {}
}
