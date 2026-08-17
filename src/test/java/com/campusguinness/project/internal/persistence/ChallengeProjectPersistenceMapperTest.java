package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ChallengeProjectPersistenceMapper")
class ChallengeProjectPersistenceMapperTest {

    private ChallengeProjectEntity buildEntity(String status) {
        var e = new ChallengeProjectEntity();
        e.setId(UUID.randomUUID()); e.setName("test"); e.setCategory("MATH");
        e.setScoreStorageType("INTEGER"); e.setScoreIndicatorType("NUMERIC");
        e.setComparisonDirection("HIGHER_BETTER"); e.setEffectiveScoreRule("BEST");
        e.setAllowTie(false); e.setProjectStatus(status);
        e.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        e.setUpdatedAt(Instant.parse("2026-06-01T00:00:00Z"));
        return e;
    }

    @Nested @DisplayName("Entity → Domain")
    class ToDomain {
        @Test @DisplayName("restores DRAFT without events")
        void shouldRestoreDraft() {
            var entity = buildEntity("DRAFT");
            var domain = ChallengeProjectPersistenceMapper.toDomain(entity);
            assertThat(domain.status()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(domain.domainEvents()).isEmpty();
        }
        @Test @DisplayName("restores PUBLISHED with correct status and no events")
        void shouldRestorePublished() {
            var entity = buildEntity("PUBLISHED");
            var domain = ChallengeProjectPersistenceMapper.toDomain(entity);
            assertThat(domain.status()).isEqualTo(ProjectStatus.PUBLISHED);
            assertThat(domain.domainEvents()).isEmpty();
        }
        @Test @DisplayName("restores ARCHIVED with correct status and no events")
        void shouldRestoreArchived() {
            var entity = buildEntity("ARCHIVED");
            var domain = ChallengeProjectPersistenceMapper.toDomain(entity);
            assertThat(domain.status()).isEqualTo(ProjectStatus.ARCHIVED);
            assertThat(domain.domainEvents()).isEmpty();
        }

        @Test @DisplayName("restores project resources and current rule version")
        void shouldRestoreResourceFields() {
            var entity = buildEntity("PUBLISHED");
            UUID versionId = UUID.randomUUID();
            entity.setVenueRequirements("Main gym");
            entity.setEquipmentRequirements("Timer");
            entity.setRulesText("Complete rules");
            entity.setCurrentRuleVersionId(versionId);

            var domain = ChallengeProjectPersistenceMapper.toDomain(entity);

            assertThat(domain.venueRequirements()).isEqualTo("Main gym");
            assertThat(domain.equipmentRequirements()).isEqualTo("Timer");
            assertThat(domain.scoreConfig().rulesText()).isEqualTo("Complete rules");
            assertThat(domain.currentRuleVersionId()).isEqualTo(versionId);
        }
    }

    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test @DisplayName("maps DRAFT domain to entity")
        void shouldMapToEntity() {
            var domain = ChallengeProject.create(new ChallengeProjectId(UUID.randomUUID()),
                    new ProjectName("test"), new ProjectCategory("MATH"),
                    new ScoreConfig(ScoreStorageType.INTEGER, ScoreIndicatorType.NUMERIC,
                            ComparisonDirection.HIGHER_BETTER, null, null, "BEST", null, null, false),
                    "desc");
            var entity = ChallengeProjectPersistenceMapper.toEntity(domain);
            assertThat(entity.getId()).isEqualTo(domain.id().value());
            assertThat(entity.getProjectStatus()).isEqualTo("DRAFT");
        }

        @Test @DisplayName("updates entity without replacing createdAt")
        void shouldPreserveCreatedAtWhenUpdating() {
            var entity = buildEntity("DRAFT");
            Instant createdAt = entity.getCreatedAt();
            var domain = ChallengeProject.create(new ChallengeProjectId(entity.getId()),
                    new ProjectName("updated"), new ProjectCategory("ATHLETICS"),
                    new ScoreConfig(ScoreStorageType.INTEGER, ScoreIndicatorType.NUMERIC,
                            ComparisonDirection.HIGHER_BETTER, "points", null, "BEST",
                            null, "Updated rules", false), "desc", "Gym", "Timer");

            ChallengeProjectPersistenceMapper.updateEntity(domain, entity);

            assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
            assertThat(entity.getRulesText()).isEqualTo("Updated rules");
            assertThat(entity.getVenueRequirements()).isEqualTo("Gym");
        }
    }
}
