package com.campusguinness.interfaces.web.challengeproject;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.project.application.command.CreateChallengeProjectCommand;
import com.campusguinness.project.application.query.ChallengeProjectQueryService;
import com.campusguinness.project.application.result.ChallengeProjectResult;
import com.campusguinness.project.application.service.ChallengeProjectApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/challenge-projects")
public class ChallengeProjectController {

    private final ChallengeProjectApplicationService service;
    private final ChallengeProjectQueryService queryService;

    public ChallengeProjectController(ChallengeProjectApplicationService service, ChallengeProjectQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ChallengeProjectListItem>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listPublic(page, size);
        var items = result.items().stream()
                .map(r -> new ChallengeProjectListItem(r.projectId(), r.name(), r.category(),
                        r.scoreStorageType(), r.comparisonDirection(), r.projectStatus(), r.createdAt()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ChallengeProjectResponse> create(@Valid @RequestBody CreateChallengeProjectRequest req) {
        var cmd = new CreateChallengeProjectCommand(
                req.name(), req.category(), req.scoreStorageType(), req.scoreIndicatorType(),
                req.comparisonDirection(), req.effectiveScoreRule(), req.allowTie(),
                req.scoreUnit(), req.decimalPlaces(), req.gradeOrder(), req.rulesText(), req.description(),
                req.venueRequirements(), req.equipmentRequirements());
        ChallengeProjectResult result = service.create(cmd);
        return ResponseEntity.created(URI.create("/api/v1/challenge-projects/" + result.id()))
                .body(new ChallengeProjectResponse(result.id(), result.name(), result.status()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChallengeProjectResponse> get(@PathVariable UUID id) {
        var project = service.findById(id);
        return ResponseEntity.ok(new ChallengeProjectResponse(
                project.id().value(), project.name().value(), project.status().name()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ChallengeProjectResponse> publish(@PathVariable UUID id) {
        ChallengeProjectResult result = service.publish(id);
        return ResponseEntity.ok(new ChallengeProjectResponse(result.id(), result.name(), result.status()));
    }
}
