package com.campusguinness.interfaces.web.rankingdefinition;

import com.campusguinness.ranking.application.result.RankingDefinitionResult;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.application.result.RankingPublicationResult;
import com.campusguinness.ranking.application.service.L3RankingDefinitionApplicationService;
import com.campusguinness.ranking.application.service.L3RankingManagementApplicationService;
import com.campusguinness.ranking.application.service.RankingGenerationApplicationService;
import com.campusguinness.ranking.application.service.RankingManagementQueryService;
import com.campusguinness.ranking.application.service.RankingPublicationApplicationService;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.interfaces.web.rankingmanagement.RankingManagementResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin/ranking-definitions")
public class SuperAdminRankingDefinitionController {
    private final L3RankingDefinitionApplicationService service;
    private final RankingGenerationApplicationService generationService;
    private final RankingPublicationApplicationService publicationService;
    private final RankingManagementQueryService managementQuery;
    private final L3RankingManagementApplicationService managementService;

    public SuperAdminRankingDefinitionController(
            L3RankingDefinitionApplicationService service,
            RankingGenerationApplicationService generationService,
            RankingPublicationApplicationService publicationService,
            RankingManagementQueryService managementQuery,
            L3RankingManagementApplicationService managementService) {
        this.service = service;
        this.generationService = generationService;
        this.publicationService = publicationService;
        this.managementQuery = managementQuery;
        this.managementService = managementService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<RankingManagementResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = managementQuery.listL3(page, size);
        return ResponseEntity.ok(PageResponse.of(
                result.items().stream().map(RankingManagementResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingManagementResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(RankingManagementResponse.from(managementQuery.detailL3(id)));
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

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> enable(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(managementService.enable(id)));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RankingDefinitionResponse> disable(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(managementService.disable(id)));
    }

    private RankingDefinitionResponse toResponse(RankingDefinitionResult result) {
        return new RankingDefinitionResponse(result.id(), result.enabled());
    }
}
