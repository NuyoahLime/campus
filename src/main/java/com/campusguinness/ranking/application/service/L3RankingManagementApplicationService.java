package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
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
public class L3RankingManagementApplicationService {
    private final RankingDefinitionRepository definitions;
    private final PlatformGovernanceAuthorization authorization;

    public L3RankingManagementApplicationService(
            RankingDefinitionRepository definitions,
            PlatformGovernanceAuthorization authorization) {
        this.definitions = definitions;
        this.authorization = authorization;
    }

    public RankingDefinitionResult disable(UUID id) {
        RankingDefinition definition = findAuthorizedL3(id);
        definition.disable();
        definitions.save(definition);
        return new RankingDefinitionResult(id, definition.isEnabled());
    }

    public RankingDefinitionResult enable(UUID id) {
        RankingDefinition definition = findAuthorizedL3(id);
        definition.enable();
        definitions.save(definition);
        return new RankingDefinitionResult(id, definition.isEnabled());
    }

    private RankingDefinition findAuthorizedL3(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("rankingDefinitionId required");
        }
        authorization.requireSuperAdmin();
        RankingDefinition definition = definitions.findByIdForUpdate(new RankingDefinitionId(id))
                .orElseThrow(() -> new IllegalArgumentException("RankingDefinition not found: " + id));
        if (definition.layer() != RankingLayer.L3 || definition.schoolId() != null) {
            throw new IllegalStateException(
                    "Cannot change ranking definition: only platform-scoped L3 definitions are supported.");
        }
        return definition;
    }
}
