package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.ranking.application.result.RankingDefinitionResult;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.application.result.RankingPublicationResult;
import com.campusguinness.ranking.application.service.L3RankingDefinitionApplicationService;
import com.campusguinness.ranking.application.service.RankingGenerationApplicationService;
import com.campusguinness.ranking.application.service.RankingPublicationApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin/ranking-definitions")
public class SuperAdminRankingDefinitionController {
    private final L3RankingDefinitionApplicationService service;
    private final RankingGenerationApplicationService generationService;
    private final RankingPublicationApplicationService publicationService;

    public SuperAdminRankingDefinitionController(
            L3RankingDefinitionApplicationService service,
            RankingGenerationApplicationService generationService,
            RankingPublicationApplicationService publicationService) {
        this.service = service;
        this.generationService = generationService;
        this.publicationService = publicationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> create(@Valid @RequestBody CreateL3RankingDefinitionRequest req) {
        RankingDefinitionResult result = service.create(req.name(), req.projectId(), req.ruleVersionId());
        return ResponseEntity.created(URI.create("/api/v1/super-admin/ranking-definitions/" + result.id()))
                .body(new RankingDefinitionResponse(result.id(), result.enabled()));
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingGenerationResponse> generate(@PathVariable UUID id) {
        RankingGenerationResult result = generationService.generate(id);
        return ResponseEntity.ok(RankingGenerationResponse.from(result));
    }

    @PostMapping("/{definitionId}/versions/{versionId}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingPublicationResponse> publish(
            @PathVariable UUID definitionId,
            @PathVariable UUID versionId) {
        RankingPublicationResult result = publicationService.publish(definitionId, versionId);
        return ResponseEntity.ok(RankingPublicationResponse.from(result));
    }
}
