package com.campusguinness.ranking.application.query.port;

import com.campusguinness.ranking.application.query.model.RankingGenerationContext;
import com.campusguinness.ranking.application.query.model.RankingGenerationSourceRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface RankingGenerationQueryPort {
    Optional<RankingGenerationContext> findContext(UUID activityProjectId);

    List<RankingGenerationSourceRow> findAuthoritativeEffectiveScores(UUID activityProjectId, UUID schoolId);

    List<RankingGenerationContext> findL2CandidateContexts(
            UUID projectId,
            UUID schoolId,
            String grade,
            String className,
            Instant activityPeriodStart,
            Instant activityPeriodEnd);

    Optional<RankingGenerationContext> findL2FallbackContext(UUID projectId, UUID schoolId);

    List<RankingGenerationSourceRow> findL2AuthoritativeEffectiveScores(
            UUID projectId,
            UUID schoolId,
            UUID ruleVersionId,
            String grade,
            String className,
            Instant activityPeriodStart,
            Instant activityPeriodEnd);
}
