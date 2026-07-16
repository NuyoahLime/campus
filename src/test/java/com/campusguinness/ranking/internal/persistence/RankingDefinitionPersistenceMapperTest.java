package com.campusguinness.ranking.internal.persistence;

import com.campusguinness.ranking.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("RankingDefinitionPersistenceMapper")
class RankingDefinitionPersistenceMapperTest {
    @Nested class ToDomain {
        @Test void restoresEnabled() {
            var e = entity(); e.setEnabled(true);
            var r = RankingDefinitionPersistenceMapper.toDomain(e);
            assertThat(r.isEnabled()).isTrue(); assertThat(r.domainEvents()).isEmpty();
        }
        @Test void restoresDisabled() {
            var e = entity(); e.setEnabled(false);
            var r = RankingDefinitionPersistenceMapper.toDomain(e);
            assertThat(r.isEnabled()).isFalse(); assertThat(r.domainEvents()).isEmpty();
        }
    }
    @Nested class ToEntity {
        @Test void mapsToEntity() {
            var r = RankingDefinition.create(new RankingDefinition.Builder()
                    .id(new RankingDefinitionId(UUID.randomUUID())).layer(RankingLayer.L1)
                    .name("test").projectId(UUID.randomUUID()).createdBy(UUID.randomUUID()));
            var e = RankingDefinitionPersistenceMapper.toEntity(r);
            assertThat(e.isEnabled()).isTrue(); assertThat(e.getLayer()).isEqualTo("L1");
        }
    }
    private RankingDefinitionEntity entity() {
        var e = new RankingDefinitionEntity(); e.setId(UUID.randomUUID()); e.setLayer("L1");
        e.setName("test"); e.setProjectId(UUID.randomUUID()); e.setCreatedBy(UUID.randomUUID());
        e.setEnabled(true);
        return e;
    }
}
