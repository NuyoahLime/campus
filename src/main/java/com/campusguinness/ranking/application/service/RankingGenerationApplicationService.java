package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.application.port.RankingGenerationRepository;
import com.campusguinness.ranking.application.query.port.RankingGenerationQueryPort;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingDefinitionId;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RankingGenerationApplicationService {
    private final RankingDefinitionRepository definitions;
    private final RankingGenerationQueryPort sourceQuery;
    private final RankingGenerationRepository generationRepository;
    private final SchoolResourceAuthorization authorization;
    private final RankingGenerationCalculator calculator = new RankingGenerationCalculator();

    public RankingGenerationApplicationService(
            RankingDefinitionRepository definitions,
            RankingGenerationQueryPort sourceQuery,
            RankingGenerationRepository generationRepository,
            SchoolResourceAuthorization authorization) {
        this.definitions = definitions;
        this.sourceQuery = sourceQuery;
        this.generationRepository = generationRepository;
        this.authorization = authorization;
    }

    public RankingGenerationResult generate(UUID rankingDefinitionId) {
        RankingDefinition definition = definitions.findById(new RankingDefinitionId(rankingDefinitionId))
                .orElseThrow(() -> new IllegalArgumentException("RankingDefinition not found: " + rankingDefinitionId));
        if (definition.layer() != RankingLayer.L1) {
            throw new IllegalStateException("Cannot generate ranking: Phase1 supports only L1 definitions.");
        }
        if (!definition.isEnabled()) {
            throw new IllegalStateException("Cannot generate ranking: definition is disabled.");
        }
        if (definition.schoolId() == null) {
            throw new IllegalStateException("Cannot generate ranking: L1 generation requires a school-scoped definition.");
        }
        authorization.requireSchoolAdmin(definition.schoolId());
        RankingGenerationScope scope = RankingGenerationScope.fromDimensionFilters(definition.dimensionFilters());
        var context = sourceQuery.findContext(scope.activityProjectId())
                .orElseThrow(() -> new IllegalArgumentException("ActivityProject not found: " + scope.activityProjectId()));
        if (!definition.schoolId().equals(context.schoolId()) || !definition.projectId().equals(context.projectId())) {
            throw new IllegalStateException("Cannot generate ranking: definition scope does not match activity project.");
        }
        var sources = sourceQuery.findAuthoritativeEffectiveScores(scope.activityProjectId(), definition.schoolId());
        var snapshot = calculator.calculate(context, sources);
        return generationRepository.saveGeneratedSnapshot(definition, scope, context, snapshot);
    }
}
