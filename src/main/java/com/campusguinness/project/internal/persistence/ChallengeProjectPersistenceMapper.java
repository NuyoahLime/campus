package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.internal.domain.*;
import java.time.Instant;

/** Maps ChallengeProject domain aggregate ↔ JPA entity. Infrastructure concern only. */
final class ChallengeProjectPersistenceMapper {

    private ChallengeProjectPersistenceMapper() {}

    static ChallengeProjectEntity toEntity(ChallengeProject domain) {
        ChallengeProjectEntity e = new ChallengeProjectEntity();
        e.setId(domain.id().value());
        copyMutableFields(domain, e);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    /** Copy domain fields onto an existing managed entity — preserves currentRuleVersionId, createdAt, version. */
    static void copyMutableFields(ChallengeProject domain, ChallengeProjectEntity e) {
        e.setName(domain.name().value());
        e.setCategory(domain.category().value());
        e.setDescription(domain.description());
        e.setVenueRequirements(domain.venueRequirements());
        e.setEquipmentRequirements(domain.equipmentRequirements());
        e.setScoreStorageType(domain.scoreConfig().storageType().name());
        e.setScoreIndicatorType(domain.scoreConfig().indicatorType().name());
        e.setComparisonDirection(domain.scoreConfig().comparisonDirection().name());
        e.setScoreUnit(domain.scoreConfig().scoreUnit());
        e.setDecimalPlaces(domain.scoreConfig().decimalPlaces());
        e.setGradeOrder(domain.scoreConfig().gradeOrder());
        e.setAllowTie(domain.scoreConfig().allowTie());
        e.setEffectiveScoreRule(domain.scoreConfig().effectiveScoreRule());
        e.setRulesText(domain.scoreConfig().rulesText());
        e.setProjectStatus(domain.status().name());
        e.setUpdatedAt(Instant.now());
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
                entity.getVenueRequirements(),
                entity.getEquipmentRequirements(),
                ProjectStatus.valueOf(entity.getProjectStatus()));
    }
}
