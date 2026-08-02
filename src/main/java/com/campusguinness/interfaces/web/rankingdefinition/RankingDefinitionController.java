package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.ranking.application.result.RankingDefinitionResult;
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
    private final CurrentActor currentActor;
    public RankingDefinitionController(RankingDefinitionApplicationService s, CurrentActor currentActor) {
        this.service = s; this.currentActor = currentActor;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> create(@Valid @RequestBody CreateRankingDefinitionRequest req) {
        var r = service.create(RankingLayer.valueOf(req.layer()), req.name(), req.schoolId(), req.projectId(), currentActor.requireUserId());
        return ResponseEntity.created(URI.create("/api/v1/ranking-definitions/" + r.id()))
                .body(new RankingDefinitionResponse(r.id(), r.enabled()));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> enable(@PathVariable UUID id) {
        var r = service.enable(id);
        return ResponseEntity.ok(new RankingDefinitionResponse(r.id(), r.enabled()));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> disable(@PathVariable UUID id) {
        var r = service.disable(id);
        return ResponseEntity.ok(new RankingDefinitionResponse(r.id(), r.enabled()));
    }
}
