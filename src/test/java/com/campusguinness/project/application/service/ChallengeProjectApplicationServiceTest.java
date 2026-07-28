package com.campusguinness.project.application.service;

import com.campusguinness.project.application.command.CreateChallengeProjectCommand;
import com.campusguinness.project.application.exception.ChallengeProjectNotFoundException;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.application.port.ProjectRuleVersionPort;
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
    @Mock
    private ProjectRuleVersionPort ruleVersionPort;

    private ChallengeProjectApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ChallengeProjectApplicationService(repository, ruleVersionPort);
    }

    private CreateChallengeProjectCommand validCommand() {
        return new CreateChallengeProjectCommand(
                "校园数学挑战赛", "MATH", "INTEGER", "NUMERIC",
                "HIGHER_BETTER", "BEST", false,
                "次", null, null, null,
                "面向全校的数学竞赛活动", null, null);
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
                    null, null, null, null, "desc", null, null);

            assertThatThrownBy(() -> service.create(cmd))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("persists content fields correctly")
        void shouldPersistContentFields() {
            var cmd = new CreateChallengeProjectCommand(
                    "测试项目", "SPEED", "INTEGER", "NUMERIC",
                    "HIGHER_BETTER", "BEST", false,
                    "秒", null, null, "比赛规则文本",
                    "描述", "需要跑道", "需要计时器");

            ArgumentCaptor<ChallengeProject> captor = ArgumentCaptor.forClass(ChallengeProject.class);
            service.create(cmd);

            verify(repository).save(captor.capture());
            ChallengeProject saved = captor.getValue();
            assertThat(saved.description()).isEqualTo("描述");
            assertThat(saved.venueRequirements()).isEqualTo("需要跑道");
            assertThat(saved.equipmentRequirements()).isEqualTo("需要计时器");
            assertThat(saved.scoreConfig().rulesText()).isEqualTo("比赛规则文本");
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
                    "desc", null, null);
            when(repository.findById(any())).thenReturn(Optional.of(project));

            ChallengeProjectResult result = service.publish(id, UUID.randomUUID());

            assertThat(result.status()).isEqualTo("PUBLISHED");
            verify(repository).save(any(ChallengeProject.class));
            verify(ruleVersionPort).createInitialRuleVersion(any(), any(ProjectRuleVersionPort.InitialRuleVersionSnapshot.class), any());
        }
    }
}
