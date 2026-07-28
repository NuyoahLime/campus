package com.campusguinness.project.internal.persistence;

import com.campusguinness.project.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChallengeProjectRepositoryAdapter")
class ChallengeProjectRepositoryAdapterTest {

    @Mock private ChallengeProjectJpaRepository jpaRepository;
    @InjectMocks private ChallengeProjectRepositoryAdapter adapter;

    private ChallengeProject createDraft() {
        return ChallengeProject.create(
                new ChallengeProjectId(UUID.randomUUID()), new ProjectName("test"),
                new ProjectCategory("MATH"),
                new ScoreConfig(ScoreStorageType.INTEGER, ScoreIndicatorType.NUMERIC,
                        ComparisonDirection.HIGHER_BETTER, null, null, "BEST", null, null, false),
                "desc", null, null);
    }

    @Nested @DisplayName("save")
    class Save {
        @Test @DisplayName("creates new entity when project not yet persisted")
        void shouldSaveNew() {
            var project = createDraft();
            when(jpaRepository.findById(project.id().value())).thenReturn(Optional.empty());
            adapter.save(project);
            ArgumentCaptor<ChallengeProjectEntity> captor = ArgumentCaptor.forClass(ChallengeProjectEntity.class);
            verify(jpaRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(project.id().value());
            assertThat(captor.getValue().getProjectStatus()).isEqualTo("DRAFT");
        }

        @Test @DisplayName("updates managed entity preserving currentRuleVersionId")
        void shouldUpdateExisting() {
            var project = createDraft();
            project.publish();
            var existingEntity = buildEntity(project.id().value(), "DRAFT");
            existingEntity.setCurrentRuleVersionId(UUID.randomUUID());
            when(jpaRepository.findById(project.id().value())).thenReturn(Optional.of(existingEntity));
            adapter.save(project);
            verify(jpaRepository, never()).save(any(ChallengeProjectEntity.class));
            assertThat(existingEntity.getProjectStatus()).isEqualTo("PUBLISHED");
            assertThat(existingEntity.getCurrentRuleVersionId()).isNotNull(); // preserved
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("restores domain aggregate without domain events")
        void shouldRestoreWithoutEvents() {
            UUID id = UUID.randomUUID();
            var entity = buildEntity(id, "PUBLISHED");
            when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));
            var restored = adapter.findById(new ChallengeProjectId(id));
            assertThat(restored).isPresent();
            assertThat(restored.get().status()).isEqualTo(ProjectStatus.PUBLISHED);
            assertThat(restored.get().domainEvents()).isEmpty(); // no events from restore
        }

        @Test @DisplayName("returns empty when not found")
        void shouldReturnEmpty() {
            when(jpaRepository.findById(any())).thenReturn(Optional.empty());
            assertThat(adapter.findById(new ChallengeProjectId(UUID.randomUUID()))).isEmpty();
        }

        @Test @DisplayName("restores ARCHIVED correctly")
        void shouldRestoreArchived() {
            UUID id = UUID.randomUUID();
            var entity = buildEntity(id, "ARCHIVED");
            when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));
            var restored = adapter.findById(new ChallengeProjectId(id));
            assertThat(restored).isPresent();
            assertThat(restored.get().status()).isEqualTo(ProjectStatus.ARCHIVED);
        }
    }

    private ChallengeProjectEntity buildEntity(UUID id, String status) {
        var e = new ChallengeProjectEntity();
        e.setId(id); e.setName("test"); e.setCategory("MATH");
        e.setScoreStorageType("INTEGER"); e.setScoreIndicatorType("NUMERIC");
        e.setComparisonDirection("HIGHER_BETTER"); e.setEffectiveScoreRule("BEST");
        e.setAllowTie(false); e.setProjectStatus(status);
        return e;
    }
}
