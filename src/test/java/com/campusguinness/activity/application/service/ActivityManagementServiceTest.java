package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityManagementServiceTest {
    @Mock ActivityRepository repo;
    @Mock ActivityProjectPort projectPort;
    @Mock ChallengeProjectRepository projectRepo;
    @Mock ResponsibleTeacherPort teacherPort;
    @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;
    ActivityManagementService svc;
    @BeforeEach void setUp() { svc = new ActivityManagementService(repo, projectPort, projectRepo, teacherPort, jdbc); }

    @Nested class Create {
        @Test void shouldCreate() {
            var r = svc.create(new CreateActivityCommand(UUID.randomUUID(), UUID.randomUUID(), "t", "d", null, null, null));
            assertThat(r.executionStatus()).isEqualTo("DRAFT");
            assertThat(r.publicStatus()).isEqualTo("NOT_SUBMITTED");
            verify(repo).save(any());
        }
    }
    @Nested class Publish {
        @Test void shouldPublish() {
            var a = draft(); when(repo.findById(any())).thenReturn(Optional.of(a));
            assertThat(svc.publish(a.id().value()).executionStatus()).isEqualTo("PUBLISHED");
            verify(repo).save(any());
        }
    }
    @Nested class Errors {
        @Test void shouldThrowWhenNotFound() {
            when(repo.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.publish(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
    }
    private Activity draft() {
        return Activity.create(new Activity.Builder().id(new ActivityId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID()).createdBy(UUID.randomUUID()).title("t"));
    }
}
