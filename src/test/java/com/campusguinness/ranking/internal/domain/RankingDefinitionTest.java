package com.campusguinness.ranking.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RankingDefinition aggregate")
class RankingDefinitionTest {

    private RankingDefinition.Builder validBuilder() {
        return new RankingDefinition.Builder()
                .id(new RankingDefinitionId(UUID.randomUUID()))
                .layer(RankingLayer.L1)
                .name("校园数学挑战赛排行榜")
                .schoolId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .tieBreakRule("BEST")
                .createdBy(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("creates with isEnabled=true")
        void shouldCreateEnabled() {
            var r = RankingDefinition.create(validBuilder());
            assertThat(r.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("null id rejected")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> RankingDefinition.create(validBuilder().id(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null layer rejected")
        void shouldRejectNullLayer() {
            assertThatThrownBy(() -> RankingDefinition.create(validBuilder().layer(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null name rejected")
        void shouldRejectNullName() {
            assertThatThrownBy(() -> RankingDefinition.create(validBuilder().name(null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("name over 200 chars rejected")
        void shouldRejectTooLongName() {
            assertThatThrownBy(() -> RankingDefinition.create(validBuilder().name("A".repeat(201))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("L1 layer")
        void shouldSupportL1Layer() {
            var r = RankingDefinition.create(validBuilder().layer(RankingLayer.L1));
            assertThat(r.layer()).isEqualTo(RankingLayer.L1);
        }

        @Test
        @DisplayName("L2 layer")
        void shouldSupportL2Layer() {
            var r = RankingDefinition.create(validBuilder().layer(RankingLayer.L2));
            assertThat(r.layer()).isEqualTo(RankingLayer.L2);
        }

        @Test
        @DisplayName("L3 layer")
        void shouldSupportL3Layer() {
            var r = RankingDefinition.create(validBuilder().layer(RankingLayer.L3));
            assertThat(r.layer()).isEqualTo(RankingLayer.L3);
        }
    }

    @Nested
    @DisplayName("Enable / disable")
    class EnableDisable {

        @Test
        @DisplayName("disable sets enabled=false")
        void shouldDisable() {
            var r = RankingDefinition.create(validBuilder());
            r.disable();
            assertThat(r.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("enable sets enabled=true")
        void shouldEnable() {
            var r = RankingDefinition.create(validBuilder());
            r.disable();
            r.enable();
            assertThat(r.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("double disable throws")
        void shouldRejectDoubleDisable() {
            var r = RankingDefinition.create(validBuilder());
            r.disable();
            assertThatThrownBy(r::disable).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("double enable throws")
        void shouldRejectDoubleEnable() {
            var r = RankingDefinition.create(validBuilder());
            assertThatThrownBy(r::enable).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Version reference")
    class VersionReference {

        @Test
        @DisplayName("currentVersionId starts null")
        void shouldStartWithNullVersion() {
            var r = RankingDefinition.create(validBuilder());
            assertThat(r.currentVersionId()).isNull();
        }

        @Test
        @DisplayName("setCurrentVersionId updates reference")
        void shouldSetCurrentVersionId() {
            var r = RankingDefinition.create(validBuilder());
            UUID vid = UUID.randomUUID();
            r.setCurrentVersionId(vid);
            assertThat(r.currentVersionId()).isEqualTo(vid);
        }
    }

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtection {
        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            var r = RankingDefinition.create(validBuilder());
            assertThatThrownBy(() -> r.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
