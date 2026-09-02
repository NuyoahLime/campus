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
    private final L2CandidateSelectionService l2CandidateSelection = new L2CandidateSelectionService();

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
        RankingDefinition definition = definitions.findByIdForUpdate(new RankingDefinitionId(rankingDefinitionId))
                .orElseThrow(() -> new IllegalArgumentException("RankingDefinition not found: " + rankingDefinitionId));
        if (definition.layer() == RankingLayer.L3) {
            throw new IllegalStateException("Cannot generate ranking: Phase4B supports only L1 and L2 definitions.");
        }
        if (!definition.isEnabled()) {
            throw new IllegalStateException("Cannot generate ranking: definition is disabled.");
        }
        if (definition.schoolId() == null) {
            throw new IllegalStateException("Cannot generate ranking: generation requires a school-scoped definition.");
        }
        authorization.requireSchoolAdmin(definition.schoolId());
        if (definition.layer() == RankingLayer.L2) {
            return generateL2(definition);
        }
        return generateL1(definition);
    }

    private RankingGenerationResult generateL1(RankingDefinition definition) {
        RankingGenerationScope scope = RankingGenerationScope.l1FromDimensionFilters(definition.dimensionFilters());
        var context = sourceQuery.findContext(scope.activityProjectId())
                .orElseThrow(() -> new IllegalArgumentException("ActivityProject not found: " + scope.activityProjectId()));
        if (!definition.schoolId().equals(context.schoolId()) || !definition.projectId().equals(context.projectId())) {
            throw new IllegalStateException("Cannot generate ranking: definition scope does not match activity project.");
        }
        var sources = sourceQuery.findAuthoritativeEffectiveScores(scope.activityProjectId(), definition.schoolId());
        var snapshot = calculator.calculate(context, sources);
        return generationRepository.saveGeneratedSnapshot(definition, scope, context, snapshot);
    }

    private RankingGenerationResult generateL2(RankingDefinition definition) {
        RankingGenerationScope scope = RankingGenerationScope.l2FromDimensionFilters(definition.dimensionFilters());
        var contexts = sourceQuery.findL2CandidateContexts(
                definition.projectId(),
                definition.schoolId(),
                scope.grade(),
                scope.className(),
                scope.activityPeriodStart(),
                scope.activityPeriodEnd());
        if (contexts.size() > 1) {
            throw new IllegalStateException("Cannot generate ranking: L2 candidates span multiple RuleVersions.");
        }
        var context = contexts.isEmpty()
                ? sourceQuery.findL2FallbackContext(definition.projectId(), definition.schoolId())
                    .orElseThrow(() -> new IllegalArgumentException("ChallengeProject not found: " + definition.projectId()))
                : contexts.getFirst();
        if (!definition.schoolId().equals(context.schoolId()) || !definition.projectId().equals(context.projectId())) {
            throw new IllegalStateException("Cannot generate ranking: definition scope does not match challenge project.");
        }
        var sources = sourceQuery.findL2AuthoritativeEffectiveScores(
                definition.projectId(),
                definition.schoolId(),
                context.ruleVersionId(),
                scope.grade(),
                scope.className(),
                scope.activityPeriodStart(),
                scope.activityPeriodEnd());
        var selected = l2CandidateSelection.selectBestScores(context, sources);
        var snapshot = calculator.calculate(context, selected);
        return generationRepository.saveGeneratedSnapshot(definition, scope, context, snapshot);
    }
}
