package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.internal.domain.*;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.port.ChallengeProjectRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
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
    @Mock SchoolMembershipQueryPort membershipPort;
    ActivityManagementService svc;

    @BeforeEach void setUp() {
        svc = new ActivityManagementService(repo, projectPort, projectRepo, teacherPort, membershipPort);
    }

    private Activity draft() {
        return Activity.create(new Activity.Builder().id(new ActivityId(UUID.randomUUID()))
                .schoolId(UUID.randomUUID()).createdBy(UUID.randomUUID()).title("t"));
    }

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
            var a = draft();
            a.updateTitle("Test Activity");
            a.updateTimeRange(Instant.now(), Instant.now().plusSeconds(3600));
            a.updateLocation("Room 101");
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivity(any())).thenReturn(List.of(
                    new ActivityProjectPort.ProjectRecord(UUID.randomUUID(), a.id().value(), UUID.randomUUID())));
            assertThat(svc.publish(a.id().value()).executionStatus()).isEqualTo("PUBLISHED");
            verify(repo).save(any());
        }
        @Test void shouldRejectWhenNoProjects() {
            var a = draft();
            a.updateTitle("Test Activity");
            a.updateTimeRange(Instant.now(), Instant.now().plusSeconds(3600));
            a.updateLocation("Room 101");
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivity(any())).thenReturn(List.of());
            assertThatThrownBy(() -> svc.publish(a.id().value()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("project");
        }
        @Test void shouldRejectWhenMissingLocation() {
            var a = draft();
            a.updateTitle("Test Activity");
            a.updateTimeRange(Instant.now(), Instant.now().plusSeconds(3600));
            when(repo.findById(any())).thenReturn(Optional.of(a));
            assertThatThrownBy(() -> svc.publish(a.id().value()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("location");
        }
    }

    @Nested class Update {
        @Test void shouldUpdateDraft() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            var r = svc.update(a.id().value(), "New Title", "New Desc", null, null, "New Location");
            assertThat(r.executionStatus()).isEqualTo("DRAFT");
            verify(repo).save(any());
        }
    }

    @Nested class Lifecycle {
        @Test void shouldBeginExecution() {
            var a = draft();
            a.publish();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            var r = svc.beginExecution(a.id().value());
            assertThat(r.executionStatus()).isEqualTo("IN_PROGRESS");
        }
        @Test void shouldFinish() {
            var a = draft();
            a.publish();
            a.beginExecution();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            var r = svc.finish(a.id().value());
            assertThat(r.executionStatus()).isEqualTo("ENDED");
        }
        @Test void shouldCancel() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            var r = svc.cancel(a.id().value());
            assertThat(r.executionStatus()).isEqualTo("CANCELLED");
        }
    }

    @Nested class PublicReview {
        @Test void shouldSubmitForReview() {
            var a = draft();
            a.publish();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            var r = svc.submitForPublicReview(a.id().value());
            assertThat(r.publicStatus()).isEqualTo("PENDING_PLATFORM_REVIEW");
        }
        @Test void shouldPlatformApprove() {
            var a = draft();
            a.publish();
            a.submitForReview();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            var r = svc.platformApprove(a.id().value());
            assertThat(r.publicStatus()).isEqualTo("PLATFORM_APPROVED");
        }
        @Test void shouldMakePublic() {
            var a = draft();
            a.publish();
            a.submitForReview();
            a.platformApprove();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            var r = svc.makePublic(a.id().value());
            assertThat(r.publicStatus()).isEqualTo("PUBLIC");
        }
    }

    @Nested class Errors {
        @Test void shouldThrowWhenNotFound() {
            when(repo.findById(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.publish(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
            verify(repo, never()).save(any());
        }
    }
}
