package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.application.port.RankingPublicationRepository;
import com.campusguinness.ranking.application.result.RankingPublicationResult;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingDefinitionId;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RankingPublicationApplicationService {
    private final RankingDefinitionRepository definitions;
    private final RankingPublicationRepository publications;
    private final SchoolResourceAuthorization authorization;
    private final PlatformGovernanceAuthorization platformAuthorization;

    public RankingPublicationApplicationService(
            RankingDefinitionRepository definitions,
            RankingPublicationRepository publications,
            SchoolResourceAuthorization authorization,
            PlatformGovernanceAuthorization platformAuthorization) {
        this.definitions = definitions;
        this.publications = publications;
        this.authorization = authorization;
        this.platformAuthorization = platformAuthorization;
    }

    public RankingPublicationResult publish(UUID rankingDefinitionId, UUID rankingVersionId) {
        RankingDefinition definition = definitions.findByIdForUpdate(new RankingDefinitionId(rankingDefinitionId))
                .orElseThrow(() -> new IllegalArgumentException("RankingDefinition not found: " + rankingDefinitionId));
        if (!definition.isEnabled()) {
            throw new IllegalStateException("Cannot publish ranking: definition is disabled.");
        }
        if (definition.layer() == RankingLayer.L3) {
            platformAuthorization.requireSuperAdmin();
        } else {
            if (definition.schoolId() == null) {
                throw new IllegalStateException("Cannot publish ranking: publication requires a school-scoped definition.");
            }
            authorization.requireSchoolAdmin(definition.schoolId());
        }
        return publications.publishGeneratedVersion(definition, rankingVersionId);
    }
}
