package com.campusguinness.project.application.service;

import com.campusguinness.project.application.command.CreateChallengeProjectCommand;
import com.campusguinness.project.application.exception.ChallengeProjectNotFoundException;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.application.result.ChallengeProjectResult;
import com.campusguinness.project.internal.domain.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChallengeProjectApplicationService")
class ChallengeProjectApplicationServiceTest {

    @Mock
    private ChallengeProjectRepository repository;

    private ChallengeProjectApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ChallengeProjectApplicationService(repository);
    }

    private CreateChallengeProjectCommand validCommand() {
        return new CreateChallengeProjectCommand(
                "校园数学挑战赛", "MATH", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "BEST", false,
                "次", null, null, null,
                "面向全校的数学竞赛活动");
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        @DisplayName("successfully creates and persists a ChallengeProject")
        void shouldCreateChallengeProject() {
            ChallengeProjectResult result = service.create(validCommand());

            assertThat(result.name()).isEqualTo("校园数学挑战赛");
            assertThat(result.status()).isEqualTo("DRAFT");
            assertThat(result.id()).isNotNull();
            verify(repository).save(any(ChallengeProject.class));
        }

        @Test
        @DisplayName("generates unique UUID for each project")
        void shouldGenerateUniqueId() {
            var r1 = service.create(validCommand());
            var r2 = service.create(validCommand());

            assertThat(r1.id()).isNotEqualTo(r2.id());
        }

        @Test
        @DisplayName("propagates domain validation errors")
        void shouldPropagateDomainException() {
            var cmd = new CreateChallengeProjectCommand(
                    "valid name", "valid category", "INVALID_TYPE", "NUMERIC",
                    "HIGHER_BETTER", "BEST", false,
                    null, null, null, null, "desc");

            assertThatThrownBy(() -> service.create(cmd))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Find by ID")
    class FindById {

        @Test
        @DisplayName("throws application exception when not found")
        void shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(repository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(id))
                    .isInstanceOf(ChallengeProjectNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Publish")
    class Publish {

        @Test
        @DisplayName("publishes a DRAFT project")
        void shouldPublishProject() {
            UUID id = UUID.randomUUID();
            ChallengeProject project = ChallengeProject.create(
                    new ChallengeProjectId(id), new ProjectName("测试"), new ProjectCategory("SCIENCE"),
                    new ScoreConfig(ScoreStorageType.INTEGER, ScoreIndicatorType.NUMERIC,
                            ComparisonDirection.HIGHER_BETTER, null, null, "BEST", null, null, false),
                    "desc");
            when(repository.findById(any())).thenReturn(Optional.of(project));

            ChallengeProjectResult result = service.publish(id, "Published for test");

            assertThat(result.status()).isEqualTo("PUBLISHED");
            verify(repository).save(any(ChallengeProject.class));
        }
    }
}
