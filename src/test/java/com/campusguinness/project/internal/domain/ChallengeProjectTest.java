package com.campusguinness.project.internal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChallengeProject aggregate")
class ChallengeProjectTest {

    // ── Value objects ──

    @Nested
    @DisplayName("ProjectName value object")
    class ProjectNameTest {

        @Test
        @DisplayName("CG-PROJECT-001: valid name is accepted")
        void shouldAcceptValidName() {
            ProjectName name = new ProjectName("百米冲刺");
            assertThat(name.value()).isEqualTo("百米冲刺");
        }

        @Test
        @DisplayName("CG-PROJECT-001: null name is rejected")
        void shouldRejectNullName() {
            assertThatThrownBy(() -> new ProjectName(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-PROJECT-001: blank name is rejected")
        void shouldRejectBlankName() {
            assertThatThrownBy(() -> new ProjectName("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-PROJECT-001: name exceeding 200 chars is rejected")
        void shouldRejectTooLongName() {
            String longName = "A".repeat(201);
            assertThatThrownBy(() -> new ProjectName(longName))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("CG-PROJECT-001: two ProjectNames with same value are equal")
        void shouldBeEqualByValue() {
            assertThat(new ProjectName("test")).isEqualTo(new ProjectName("test"));
        }
    }

    @Nested
    @DisplayName("ScoreConfig value object")
    class ScoreConfigTest {

        @Test
        @DisplayName("CG-PROJECT-001: valid INTEGER config is accepted")
        void shouldAcceptIntegerConfig() {
            ScoreConfig config = new ScoreConfig(
                    ScoreStorageType.INTEGER, ScoreIndicatorType.NUMERIC,
                    ComparisonDirection.HIGHER_BETTER, null, null, "BEST", null, null, true);
            assertThat(config.storageType()).isEqualTo(ScoreStorageType.INTEGER);
        }

        @Test
        @DisplayName("CG-PROJECT-001: DURATION type requires no score_unit")
        void shouldAcceptDurationConfig() {
            ScoreConfig config = new ScoreConfig(
                    ScoreStorageType.DURATION, ScoreIndicatorType.DURATION_MS,
                    ComparisonDirection.LOWER_BETTER, null, null, "BEST", null, null, true);
            assertThat(config.storageType()).isEqualTo(ScoreStorageType.DURATION);
        }

        @Test
        @DisplayName("CG-PROJECT-001: DECIMAL type with decimal_places is accepted")
        void shouldAcceptDecimalWithPlaces() {
            ScoreConfig config = new ScoreConfig(
                    ScoreStorageType.DECIMAL, ScoreIndicatorType.NUMERIC,
                    ComparisonDirection.HIGHER_BETTER, "秒", 2, "BEST", null, null, true);
            assertThat(config.decimalPlaces()).isEqualTo(2);
        }

        @Test
        @DisplayName("CG-PROJECT-001: null storage type is rejected")
        void shouldRejectNullStorageType() {
            assertThatThrownBy(() -> new ScoreConfig(
                    null, ScoreIndicatorType.NUMERIC, ComparisonDirection.HIGHER_BETTER,
                    null, null, "BEST", null, null, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Aggregate: creation ──

    @Nested
    @DisplayName("Creation")
    class CreationTest {

        @Test
        @DisplayName("CG-PROJECT-001: create with valid data produces DRAFT project")
        void shouldCreateProjectInDraftStatus() {
            var id = new ChallengeProjectId(UUID.randomUUID());
            var name = new ProjectName("立定跳远");
            var category = new ProjectCategory("ATHLETICS");
            var scoreConfig = new ScoreConfig(
                    ScoreStorageType.DECIMAL, ScoreIndicatorType.NUMERIC,
                    ComparisonDirection.HIGHER_BETTER, "米", 2, "BEST", null, null, true);

            ChallengeProject project = ChallengeProject.create(id, name, category, scoreConfig, null);

            assertThat(project.status()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(project.name()).isEqualTo(name);
            assertThat(project.domainEvents()).hasSize(1);
            assertThat(project.domainEvents().getFirst()).isInstanceOf(ChallengeProjectCreated.class);
        }
    }

    // ── Aggregate: state transitions ──

    @Nested
    @DisplayName("State transitions")
    class StateTransitionTest {

        private ChallengeProject createDraftProject() {
            return ChallengeProject.create(
                    new ChallengeProjectId(UUID.randomUUID()),
                    new ProjectName("测试项目"),
                    new ProjectCategory("SPEED"),
                    new ScoreConfig(ScoreStorageType.INTEGER, ScoreIndicatorType.NUMERIC,
                            ComparisonDirection.HIGHER_BETTER, null, null, "BEST", null, null, true),
                    null);
        }

        @Test
        @DisplayName("CG-PROJECT-001: DRAFT → PUBLISHED is valid")
        void shouldPublishFromDraft() {
            ChallengeProject project = createDraftProject();
            project.publish();
            assertThat(project.status()).isEqualTo(ProjectStatus.PUBLISHED);
        }

        @Test
        @DisplayName("CG-PROJECT-001: PUBLISHED → ARCHIVED is valid")
        void shouldArchiveFromPublished() {
            ChallengeProject project = createDraftProject();
            project.publish();
            project.archive();
            assertThat(project.status()).isEqualTo(ProjectStatus.ARCHIVED);
        }

        @Test
        @DisplayName("CG-PROJECT-001: ARCHIVED → PUBLISHED is valid (re-publish)")
        void shouldRepublishFromArchived() {
            ChallengeProject project = createDraftProject();
            project.publish();
            project.archive();
            project.publish();
            assertThat(project.status()).isEqualTo(ProjectStatus.PUBLISHED);
        }

        @Test
        @DisplayName("CG-PROJECT-001: DRAFT → ARCHIVED is invalid (must publish first)")
        void shouldRejectDirectArchiveFromDraft() {
            ChallengeProject project = createDraftProject();
            assertThatThrownBy(project::archive)
                    .isInstanceOf(InvalidProjectStateTransitionException.class);
        }

        @Test
        @DisplayName("CG-PROJECT-001: published project emits ProjectPublished event")
        void shouldEmitPublishedEvent() {
            ChallengeProject project = createDraftProject();
            project.clearDomainEvents();
            project.publish();
            assertThat(project.domainEvents()).anyMatch(e -> e instanceof ProjectPublished);
        }

        @Test
        @DisplayName("CG-PROJECT-001: archived project emits ProjectArchived event")
        void shouldEmitArchivedEvent() {
            ChallengeProject project = createDraftProject();
            project.publish();
            project.clearDomainEvents();
            project.archive();
            assertThat(project.domainEvents()).anyMatch(e -> e instanceof ProjectArchived);
        }
    }

    // ── Immutability protection ──

    @Nested
    @DisplayName("Collection protection")
    class CollectionProtectionTest {

        @Test
        @DisplayName("domain events list is unmodifiable")
        void domainEventsShouldNotBeModifiable() {
            ChallengeProject project = ChallengeProject.create(
                    new ChallengeProjectId(UUID.randomUUID()),
                    new ProjectName("test"),
                    new ProjectCategory("SPEED"),
                    new ScoreConfig(ScoreStorageType.INTEGER, ScoreIndicatorType.NUMERIC,
                            ComparisonDirection.HIGHER_BETTER, null, null, "BEST", null, null, true),
                    null);
            assertThatThrownBy(() -> project.domainEvents().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
