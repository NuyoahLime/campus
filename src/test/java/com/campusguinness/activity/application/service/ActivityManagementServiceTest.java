package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.project.application.port.ProjectRuleVersionPort;
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
import java.util.Map;
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
    @Mock ProjectRuleVersionPort ruleVersionPort;
    @Mock ResponsibleTeacherPort teacherPort;
    @Mock SchoolMembershipQueryPort membershipPort;
    ActivityManagementService svc;

    @BeforeEach void setUp() {
        svc = new ActivityManagementService(repo, projectPort, projectRepo, ruleVersionPort, teacherPort, membershipPort);
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
            var apId = UUID.randomUUID();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivity(any())).thenReturn(List.of(
                    new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            when(teacherPort.countAssignableByActivityProjects(any()))
                    .thenReturn(Map.of(apId, 1L));
            assertThat(svc.publish(a.id().value()).executionStatus()).isEqualTo("PUBLISHED");
            verify(repo).save(any());
        }

        @Test void publishRejectsProjectWithoutResponsibleTeacher() {
            var a = draft();
            a.updateTitle("Test Activity");
            a.updateTimeRange(Instant.now(), Instant.now().plusSeconds(3600));
            a.updateLocation("Room 101");
            var apId = UUID.randomUUID();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivity(any())).thenReturn(List.of(
                    new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            when(teacherPort.countAssignableByActivityProjects(any()))
                    .thenReturn(Map.of(apId, 0L));
            assertThatThrownBy(() -> svc.publish(a.id().value()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("responsible teacher");
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

    @Nested class ProjectConfiguration {
        private final com.campusguinness.project.internal.domain.ChallengeProject pubProject =
                com.campusguinness.project.internal.domain.ChallengeProject.reconstitute(
                        new com.campusguinness.project.internal.domain.ChallengeProjectId(UUID.randomUUID()),
                        new com.campusguinness.project.internal.domain.ProjectName("Test"),
                        new com.campusguinness.project.internal.domain.ProjectCategory("SPEED"),
                        new com.campusguinness.project.internal.domain.ScoreConfig(
                                com.campusguinness.project.internal.domain.ScoreStorageType.INTEGER,
                                com.campusguinness.project.internal.domain.ScoreIndicatorType.NUMERIC,
                                com.campusguinness.project.internal.domain.ComparisonDirection.HIGHER_BETTER,
                                null, null, "BEST", null, null, true),
                        null, null, null,
                        com.campusguinness.project.internal.domain.ProjectStatus.PUBLISHED);

        @Test void addProjectUsesCurrentRuleVersionId() {
            var a = draft();
            UUID ruleVersionId = UUID.randomUUID();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectRepo.findById(any())).thenReturn(java.util.Optional.of(pubProject));
            when(ruleVersionPort.findCurrentRuleVersionId(any())).thenReturn(Optional.of(ruleVersionId));
            when(projectPort.add(any(), any(), eq(ruleVersionId)))
                    .thenReturn(new ActivityProjectPort.ProjectRecord(UUID.randomUUID(), a.id().value(), pubProject.id().value()));
            svc.addProject(a.id().value(), pubProject.id().value());
            verify(projectPort).add(any(), any(), eq(ruleVersionId));
        }

        @Test void addProjectRejectsMissingCurrentRuleVersion() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectRepo.findById(any())).thenReturn(java.util.Optional.of(pubProject));
            when(ruleVersionPort.findCurrentRuleVersionId(any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.addProject(a.id().value(), pubProject.id().value()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no current rule version");
        }

        @Test void addProjectRequiresDraftActivity() {
            var a = draft();
            a.publish();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectRepo.findById(any())).thenReturn(java.util.Optional.of(pubProject));
            assertThatThrownBy(() -> svc.addProject(a.id().value(), pubProject.id().value()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test void removeProjectRequiresDraftActivity() {
            var a = draft();
            a.publish();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            assertThatThrownBy(() -> svc.removeProject(a.id().value(), UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    @Nested class ResponsibleTeacher {
        private final UUID apId = UUID.randomUUID();
        private final UUID teacherUserId = UUID.randomUUID();
        private final UUID membershipId = UUID.randomUUID();

        @Test void assignUsesAssignableMembership() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivityAndProject(any(), any()))
                    .thenReturn(Optional.of(new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            when(membershipPort.findAssignableTeacherMembershipId(teacherUserId, a.schoolId()))
                    .thenReturn(Optional.of(membershipId));
            when(teacherPort.exists(apId, membershipId)).thenReturn(false);
            when(teacherPort.assign(apId, membershipId, teacherUserId))
                    .thenReturn(new ResponsibleTeacherPort.TeacherRecord(UUID.randomUUID(), apId, membershipId, teacherUserId, "te", "", "", "ACTIVE", "NORMAL"));
            var result = svc.assignResponsibleTeacher(a.id().value(), UUID.randomUUID(), teacherUserId);
            assertThat(result.userId()).isEqualTo(teacherUserId);
        }

        @Test void assignRejectsCrossSchoolTeacher() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivityAndProject(any(), any()))
                    .thenReturn(Optional.of(new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            when(membershipPort.findAssignableTeacherMembershipId(teacherUserId, a.schoolId()))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.assignResponsibleTeacher(a.id().value(), UUID.randomUUID(), teacherUserId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test void assignRejectsDuplicate() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivityAndProject(any(), any()))
                    .thenReturn(Optional.of(new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            when(membershipPort.findAssignableTeacherMembershipId(teacherUserId, a.schoolId()))
                    .thenReturn(Optional.of(membershipId));
            when(teacherPort.exists(apId, membershipId)).thenReturn(true);
            assertThatThrownBy(() -> svc.assignResponsibleTeacher(a.id().value(), UUID.randomUUID(), teacherUserId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test void assignRejectsTerminalActivity() {
            var a = draft();
            a.publish(); a.beginExecution(); a.end();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            assertThatThrownBy(() -> svc.assignResponsibleTeacher(a.id().value(), UUID.randomUUID(), teacherUserId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test void unassignUsesHistoricalAssignment() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivityAndProject(any(), any()))
                    .thenReturn(Optional.of(new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            when(teacherPort.findByActivityProjectAndUserId(apId, teacherUserId))
                    .thenReturn(Optional.of(new ResponsibleTeacherPort.TeacherRecord(UUID.randomUUID(), apId, UUID.randomUUID(), teacherUserId, "te", "", "", "ENDED", "DISABLED")));
            svc.unassignResponsibleTeacher(a.id().value(), UUID.randomUUID(), teacherUserId);
            verify(teacherPort).unassignById(any());
        }

        @Test void unassignRejectsTerminalActivity() {
            var a = draft();
            a.publish(); a.beginExecution(); a.end();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            assertThatThrownBy(() -> svc.unassignResponsibleTeacher(a.id().value(), UUID.randomUUID(), teacherUserId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test void unassignLastTeacherFromPublishedRejected() {
            var a = draft();
            a.publish();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivityAndProject(any(), any()))
                    .thenReturn(Optional.of(new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            when(teacherPort.findByActivityProjectAndUserId(apId, teacherUserId))
                    .thenReturn(Optional.of(new ResponsibleTeacherPort.TeacherRecord(UUID.randomUUID(), apId, UUID.randomUUID(), teacherUserId, "te", "", "", "ACTIVE", "NORMAL")));
            when(teacherPort.countAssignableByActivityProjects(any())).thenReturn(Map.of(apId, 1L));
            assertThatThrownBy(() -> svc.unassignResponsibleTeacher(a.id().value(), UUID.randomUUID(), teacherUserId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("last assignable teacher");
        }

        @Test void assignRejectsStudent() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivityAndProject(any(), any()))
                    .thenReturn(Optional.of(new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            when(membershipPort.findAssignableTeacherMembershipId(any(), any()))
                    .thenReturn(Optional.empty()); // STUDENT won't match TEACHER+NORMAL
            assertThatThrownBy(() -> svc.assignResponsibleTeacher(a.id().value(), UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test void removeProjectDeletesTeachersBeforeProject() {
            var a = draft();
            when(repo.findById(any())).thenReturn(Optional.of(a));
            when(projectPort.findByActivityAndProject(any(), any()))
                    .thenReturn(Optional.of(new ActivityProjectPort.ProjectRecord(apId, a.id().value(), UUID.randomUUID())));
            svc.removeProject(a.id().value(), UUID.randomUUID());
            var inOrder = org.mockito.Mockito.inOrder(teacherPort, projectPort);
            inOrder.verify(teacherPort).deleteAllByActivityProject(apId);
            inOrder.verify(projectPort).remove(any(), any());
        }
    }
}
