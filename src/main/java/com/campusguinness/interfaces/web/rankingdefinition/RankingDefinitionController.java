package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.ranking.application.result.RankingDefinitionResult;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.application.service.RankingGenerationApplicationService;
import com.campusguinness.ranking.application.service.RankingDefinitionApplicationService;
import com.campusguinness.ranking.internal.domain.RankingLayer;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ranking-definitions")
public class RankingDefinitionController {
    private final RankingDefinitionApplicationService service;
    private final RankingGenerationApplicationService generationService;
    public RankingDefinitionController(RankingDefinitionApplicationService s, RankingGenerationApplicationService generationService) {
        this.service = s;
        this.generationService = generationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> create(@Valid @RequestBody CreateRankingDefinitionRequest req) {
        var r = service.create(RankingLayer.valueOf(req.layer()), req.name(), req.schoolId(), req.projectId(), req.activityProjectId());
        return ResponseEntity.created(URI.create("/api/v1/ranking-definitions/" + r.id()))
                .body(new RankingDefinitionResponse(r.id(), r.enabled()));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> enable(@PathVariable UUID id) {
        var r = service.enable(id);
        return ResponseEntity.ok(new RankingDefinitionResponse(r.id(), r.enabled()));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> disable(@PathVariable UUID id) {
        var r = service.disable(id);
        return ResponseEntity.ok(new RankingDefinitionResponse(r.id(), r.enabled()));
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<RankingGenerationResponse> generate(@PathVariable UUID id) {
        RankingGenerationResult result = generationService.generate(id);
        return ResponseEntity.ok(RankingGenerationResponse.from(result));
    }
}
