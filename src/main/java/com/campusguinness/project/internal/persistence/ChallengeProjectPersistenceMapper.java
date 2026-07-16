package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.internal.domain.*;
import java.time.Instant;

/** Maps ChallengeProject domain aggregate ↔ JPA entity. Infrastructure concern only. */
final class ChallengeProjectPersistenceMapper {

    private ChallengeProjectPersistenceMapper() {}

    static ChallengeProjectEntity toEntity(ChallengeProject domain) {
        ChallengeProjectEntity e = new ChallengeProjectEntity();
        e.setId(domain.id().value());
        e.setName(domain.name().value());
        e.setCategory(domain.category().value());
        e.setDescription(domain.description());
        e.setScoreStorageType(domain.scoreConfig().storageType().name());
        e.setScoreIndicatorType(domain.scoreConfig().indicatorType().name());
        e.setComparisonDirection(domain.scoreConfig().comparisonDirection().name());
        e.setScoreUnit(domain.scoreConfig().scoreUnit());
        e.setDecimalPlaces(domain.scoreConfig().decimalPlaces());
        e.setGradeOrder(domain.scoreConfig().gradeOrder());
        e.setAllowTie(domain.scoreConfig().allowTie());
        e.setEffectiveScoreRule(domain.scoreConfig().effectiveScoreRule());
        e.setProjectStatus(domain.status().name());
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    static ChallengeProject toDomain(ChallengeProjectEntity entity) {
        return ChallengeProject.reconstitute(
                new ChallengeProjectId(entity.getId()),
                new ProjectName(entity.getName()),
                new ProjectCategory(entity.getCategory()),
                new ScoreConfig(
                        ScoreStorageType.valueOf(entity.getScoreStorageType()),
                        ScoreIndicatorType.valueOf(entity.getScoreIndicatorType()),
                        ComparisonDirection.valueOf(entity.getComparisonDirection()),
                        entity.getScoreUnit(),
                        entity.getDecimalPlaces(),
                        entity.getEffectiveScoreRule(),
                        entity.getGradeOrder(),
                        entity.getRulesText(),
                        entity.isAllowTie()),
                entity.getDescription(),
                ProjectStatus.valueOf(entity.getProjectStatus()));
    }
}
