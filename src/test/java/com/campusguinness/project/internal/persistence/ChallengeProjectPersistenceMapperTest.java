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
        e.setDescription("desc text");
        e.setVenueRequirements("needs venue");
        e.setEquipmentRequirements("needs equipment");
        e.setRulesText("rules here");
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
        @Test @DisplayName("restores content fields correctly")
        void shouldRestoreContentFields() {
            var entity = buildEntity("DRAFT");
            var domain = ChallengeProjectPersistenceMapper.toDomain(entity);
            assertThat(domain.description()).isEqualTo("desc text");
            assertThat(domain.venueRequirements()).isEqualTo("needs venue");
            assertThat(domain.equipmentRequirements()).isEqualTo("needs equipment");
            assertThat(domain.scoreConfig().rulesText()).isEqualTo("rules here");
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
                    "desc", "needs venue", "needs equipment");
            var entity = ChallengeProjectPersistenceMapper.toEntity(domain);
            assertThat(entity.getId()).isEqualTo(domain.id().value());
            assertThat(entity.getProjectStatus()).isEqualTo("DRAFT");
            assertThat(entity.getDescription()).isEqualTo("desc");
            assertThat(entity.getVenueRequirements()).isEqualTo("needs venue");
            assertThat(entity.getEquipmentRequirements()).isEqualTo("needs equipment");
        }
    }
}
