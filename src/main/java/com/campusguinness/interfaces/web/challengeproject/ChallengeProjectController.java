package com.campusguinness.interfaces.web.challengeproject;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.project.application.command.CreateChallengeProjectCommand;
import com.campusguinness.project.application.command.UpdateChallengeProjectCommand;
import com.campusguinness.project.application.query.ChallengeProjectQueryService;
import com.campusguinness.project.application.query.model.ChallengeProjectDetailResult;
import com.campusguinness.project.application.query.model.ChallengeProjectGovernanceListResult;
import com.campusguinness.project.application.result.ChallengeProjectResult;
import com.campusguinness.project.application.service.ChallengeProjectApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/challenge-projects")
public class ChallengeProjectController {

    private final ChallengeProjectApplicationService service;
    private final ChallengeProjectQueryService queryService;

    public ChallengeProjectController(ChallengeProjectApplicationService service,
                                      ChallengeProjectQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ChallengeProjectListItem>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, name = "q") String query) {
        var result = category == null && query == null
                ? queryService.listPublic(page, size)
                : queryService.listPublic(page, size, category, query);
        var items = result.items().stream()
                .map(r -> new ChallengeProjectListItem(r.id(), r.name(), r.category(),
                        r.scoreStorageType(), r.comparisonDirection(), r.projectStatus(), r.createdAt()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/governance")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<GovernanceProjectListItem>> governanceList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, name = "q") String query) {
        var result = queryService.listGovernance(page, size, status, category, query);
        var items = result.items().stream().map(ChallengeProjectController::governanceItem).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/governance/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<GovernanceProjectResponse> governanceDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(governanceResponse(queryService.governanceDetail(id), queryService.ruleVersions(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ChallengeProjectResponse> create(@Valid @RequestBody CreateChallengeProjectRequest req) {
        var cmd = new CreateChallengeProjectCommand(req.name(), req.category(), req.scoreStorageType(),
                req.scoreIndicatorType(), req.comparisonDirection(), req.effectiveScoreRule(), req.allowTie(),
                req.scoreUnit(), req.decimalPlaces(), req.gradeOrder(), req.rulesText(), req.description(),
                req.venueRequirements(), req.equipmentRequirements());
        ChallengeProjectResult result = service.create(cmd);
        return ResponseEntity.created(URI.create("/api/v1/challenge-projects/" + result.id()))
                .body(new ChallengeProjectResponse(result.id(), result.name(), result.status()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChallengeProjectResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(response(queryService.publicDetail(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ChallengeProjectResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateChallengeProjectRequest req) {
        var cmd = new UpdateChallengeProjectCommand(req.name(), req.category(), req.scoreStorageType(),
                req.scoreIndicatorType(), req.comparisonDirection(), req.effectiveScoreRule(), req.allowTie(),
                req.scoreUnit(), req.decimalPlaces(), req.gradeOrder(), req.rulesText(), req.description(),
                req.venueRequirements(), req.equipmentRequirements());
        ChallengeProjectResult result = service.update(id, cmd);
        return ResponseEntity.ok(new ChallengeProjectResponse(result.id(), result.name(), result.status()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ChallengeProjectResponse> publish(
            @PathVariable UUID id, @RequestBody(required = false) @Valid LifecycleReasonRequest req) {
        ChallengeProjectResult result = req == null
                ? service.publish(id)
                : service.publish(id, req.reason());
        return ResponseEntity.ok(new ChallengeProjectResponse(result.id(), result.name(), result.status()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ChallengeProjectResponse> archive(
            @PathVariable UUID id, @RequestBody @Valid LifecycleReasonRequest req) {
        ChallengeProjectResult result = service.archive(id, req.reason());
        return ResponseEntity.ok(new ChallengeProjectResponse(result.id(), result.name(), result.status()));
    }

    private static GovernanceProjectListItem governanceItem(ChallengeProjectGovernanceListResult r) {
        return new GovernanceProjectListItem(r.id(), r.name(), r.category(), r.projectStatus(),
                r.scoreStorageType(), r.scoreIndicatorType(), r.comparisonDirection(), r.scoreUnit(),
                r.currentRuleVersionNumber(), r.createdAt(), r.updatedAt());
    }

    private static ChallengeProjectResponse response(ChallengeProjectDetailResult r) {
        return new ChallengeProjectResponse(r.id(), r.name(), r.category(), r.description(),
                r.venueRequirements(), r.equipmentRequirements(), r.rulesText(), r.scoreStorageType(),
                r.scoreIndicatorType(), r.comparisonDirection(), r.scoreUnit(), r.decimalPlaces(),
                r.gradeOrder(), r.allowTie(), r.effectiveScoreRule(), r.projectStatus(),
                r.currentRuleVersionId(), r.currentRuleVersionNumber(), r.createdAt(), r.updatedAt());
    }

    private static GovernanceProjectResponse governanceResponse(
            ChallengeProjectDetailResult r,
            java.util.List<com.campusguinness.project.application.query.model.ProjectRuleVersionResult> versions) {
        return new GovernanceProjectResponse(response(r), versions.stream().map(RuleVersionResponse::from).toList());
    }
}
