package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.ranking.application.port.L3AuthorizationValidationPort;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.application.result.RankingDefinitionResult;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingDefinitionId;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class L3RankingDefinitionApplicationService {
    private final RankingDefinitionRepository repo;
    private final PlatformGovernanceAuthorization authorization;
    private final L3AuthorizationValidationPort validation;

    public L3RankingDefinitionApplicationService(
            RankingDefinitionRepository repo,
            PlatformGovernanceAuthorization authorization,
            L3AuthorizationValidationPort validation) {
        this.repo = repo;
        this.authorization = authorization;
        this.validation = validation;
    }

    public RankingDefinitionResult create(String name, UUID projectId, UUID ruleVersionId) {
        UUID actorId = authorization.requireSuperAdmin();
        if (projectId == null || ruleVersionId == null) {
            throw new IllegalArgumentException("projectId and ruleVersionId are required");
        }
        validation.validateProjectRuleVersion(projectId, ruleVersionId);
        String dimensionFilters = RankingGenerationScope.normalizeL3DimensionFilters(
                "{\"ruleVersionId\":\"" + ruleVersionId + "\"}");
        var definition = RankingDefinition.create(new RankingDefinition.Builder()
                .id(new RankingDefinitionId(UUID.randomUUID()))
                .layer(RankingLayer.L3)
                .name(name)
                .schoolId(null)
                .projectId(projectId)
                .dimensionFilters(dimensionFilters)
                .createdBy(actorId));
        repo.save(definition);
        return new RankingDefinitionResult(definition.id().value(), definition.isEnabled());
    }
}
