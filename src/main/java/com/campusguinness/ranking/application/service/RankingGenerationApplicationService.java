package com.campusguinness.ranking.application.service;

import com.campusguinness.identity.application.service.PlatformGovernanceAuthorization;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.ranking.application.exception.RankingGenerationException;
import com.campusguinness.ranking.application.port.RankingDefinitionRepository;
import com.campusguinness.ranking.application.port.RankingGenerationRepository;
import com.campusguinness.ranking.application.query.model.L3GenerationCandidateRow;
import com.campusguinness.ranking.application.service.L3AuthorizationScope;
import com.campusguinness.ranking.application.query.model.L3UsableAuthorizationResult;
import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;
import com.campusguinness.ranking.application.query.port.L3UsableAuthorizationQueryPort;
import com.campusguinness.ranking.application.query.port.RankingGenerationQueryPort;
import com.campusguinness.ranking.application.result.RankingGenerationResult;
import com.campusguinness.ranking.internal.domain.RankingDefinition;
import com.campusguinness.ranking.internal.domain.RankingDefinitionId;
import com.campusguinness.ranking.internal.domain.RankingLayer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

@Service
@Transactional
public class RankingGenerationApplicationService {
    private final RankingDefinitionRepository definitions;
    private final RankingGenerationQueryPort sourceQuery;
    private final RankingGenerationRepository generationRepository;
    private final SchoolResourceAuthorization authorization;
    private final PlatformGovernanceAuthorization platformAuthorization;
    private final L3UsableAuthorizationQueryPort usableAuthorizationQuery;
    private final RankingGenerationCalculator calculator = new RankingGenerationCalculator();
    private final L2CandidateSelectionService l2CandidateSelection = new L2CandidateSelectionService();

    public RankingGenerationApplicationService(
            RankingDefinitionRepository definitions,
            RankingGenerationQueryPort sourceQuery,
            RankingGenerationRepository generationRepository,
            SchoolResourceAuthorization authorization,
            PlatformGovernanceAuthorization platformAuthorization,
            L3UsableAuthorizationQueryPort usableAuthorizationQuery) {
        this.definitions = definitions;
        this.sourceQuery = sourceQuery;
        this.generationRepository = generationRepository;
        this.authorization = authorization;
        this.platformAuthorization = platformAuthorization;
        this.usableAuthorizationQuery = usableAuthorizationQuery;
    }

    public RankingGenerationResult generate(UUID rankingDefinitionId) {
        RankingDefinition definition = definitions.findByIdForUpdate(new RankingDefinitionId(rankingDefinitionId))
                .orElseThrow(() -> new IllegalArgumentException("RankingDefinition not found: " + rankingDefinitionId));
        if (!definition.isEnabled()) {
            throw new IllegalStateException("Cannot generate ranking: definition is disabled.");
        }
        return switch (definition.layer()) {
            case L1 -> generateL1(definition);
            case L2 -> generateL2(definition);
            case L3 -> generateL3(definition);
        };
    }

    private RankingGenerationResult generateL1(RankingDefinition definition) {
        if (definition.schoolId() == null) {
            throw new IllegalStateException("Cannot generate ranking: generation requires a school-scoped definition.");
        }
        authorization.requireSchoolAdmin(definition.schoolId());
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
        if (definition.schoolId() == null) {
            throw new IllegalStateException("Cannot generate ranking: generation requires a school-scoped definition.");
        }
        authorization.requireSchoolAdmin(definition.schoolId());
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

    private RankingGenerationResult generateL3(RankingDefinition definition) {
        platformAuthorization.requireSuperAdmin();
        if (definition.schoolId() != null) {
            throw new IllegalStateException("Cannot generate ranking: L3 definitions must not be school-scoped.");
        }
        RankingGenerationScope scope = RankingGenerationScope.l3FromDimensionFilters(definition.dimensionFilters());
        var context = sourceQuery.findL3Context(definition.projectId(), scope.ruleVersionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "ChallengeProject or RuleVersion not found: " + definition.projectId()));
        var usableAuthorizations = usableAuthorizationQuery.findUsableAuthorizations(definition.projectId(), scope.ruleVersionId());
        if (usableAuthorizations.isEmpty()) {
            throw new RankingGenerationException(
                    "L3_RANKING_NO_USABLE_AUTHORIZATION",
                    "Cannot generate ranking: no usable L3 authorization.");
        }

        var parsedAuthorizations = usableAuthorizations.stream()
                .map(value -> new ParsedAuthorization(value, L3AuthorizationScope.parse(value.dataScope())))
                .toList();
        var candidates = sourceQuery.findL3CandidateScores(definition.projectId(), scope.ruleVersionId());
        var eligibleSources = new ArrayList<RankingGenerationSourceRow>();
        Map<UUID, List<UUID>> authorizationIdsByScoreAttemptId = new HashMap<>();

        for (L3GenerationCandidateRow candidate : candidates) {
            List<ParsedAuthorization> matches = parsedAuthorizations.stream()
                    .filter(auth -> matches(auth, candidate))
                    .toList();
            if (matches.isEmpty()) {
                continue;
            }
            authorizationIdsByScoreAttemptId.put(
                    candidate.scoreAttemptId(),
                    matches.stream()
                            .map(match -> match.result().id())
                            .distinct()
                            .sorted()
                            .toList());
            boolean allowSchoolName = matches.stream().anyMatch(match -> match.result().allowSchoolName());
            boolean allowStudentName = matches.stream().anyMatch(match -> match.result().allowStudentName());
            eligibleSources.add(new RankingGenerationSourceRow(
                    candidate.scoreAttemptId(),
                    candidate.studentId(),
                    allowStudentName
                            ? L3PublicIdentityMasker.maskedStudentName(candidate.studentId())
                            : L3PublicIdentityMasker.anonymousStudentName(),
                    candidate.numericValue(),
                    candidate.durationMs(),
                    candidate.scoreGrade(),
                    candidate.activityProjectId(),
                    candidate.ruleVersionId(),
                    allowSchoolName ? candidate.schoolName() : null));
        }

        var selected = l2CandidateSelection.selectBestScores(context, eligibleSources);
        var snapshot = calculator.calculate(context, selected);
        TreeSet<UUID> authorizationIds = new TreeSet<>();
        for (RankingGenerationSourceRow source : selected) {
            authorizationIds.addAll(authorizationIdsByScoreAttemptId.getOrDefault(source.scoreAttemptId(), List.of()));
        }
        snapshot = new GeneratedRankingSnapshot(snapshot.tiePolicy(), snapshot.entries(), List.copyOf(authorizationIds));
        return generationRepository.saveGeneratedSnapshot(definition, scope, context, snapshot);
    }

    private boolean matches(ParsedAuthorization authorization, L3GenerationCandidateRow candidate) {
        var scope = authorization.scope();
        if (!authorization.result().schoolId().equals(candidate.schoolId())) {
            return false;
        }
        if (!scope.activityIds().isEmpty() && !scope.activityIds().contains(candidate.activityId())) {
            return false;
        }
        if (!scope.grades().isEmpty() && !scope.grades().contains(candidate.studentGrade())) {
            return false;
        }
        if (!scope.classNames().isEmpty() && !scope.classNames().contains(candidate.studentClassName())) {
            return false;
        }
        if (scope.activityPeriodStart() != null
                && (candidate.activityStart() == null || candidate.activityStart().isBefore(scope.activityPeriodStart()))) {
            return false;
        }
        if (scope.activityPeriodEnd() != null
                && (candidate.activityEnd() == null || candidate.activityEnd().isAfter(scope.activityPeriodEnd()))) {
            return false;
        }
        return true;
    }

    private record ParsedAuthorization(L3UsableAuthorizationResult result, L3AuthorizationScope scope) {}
}
