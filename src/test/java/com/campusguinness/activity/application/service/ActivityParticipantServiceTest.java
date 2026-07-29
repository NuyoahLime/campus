package com.campusguinness.activity.application.service;

import com.campusguinness.activity.application.port.ActivityParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.activity.internal.domain.PublicStatus;
import com.campusguinness.identity.application.port.UserRepository;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityParticipantServiceTest {

    @Mock ActivityRepository activityRepo;
    @Mock ActivityProjectPort projectPort;
    @Mock ActivityParticipantPort participantPort;
    @Mock ActivityProjectParticipantPort projectParticipantPort;
    @Mock ActivityParticipantQueryPort participantQueryPort;
    @Mock SchoolMembershipQueryPort membershipPort;
    @Mock ScoreAttemptRepository scoreAttemptRepo;
    @Mock UserRepository userRepo;

    ActivityParticipantService service;

    final UUID activityId = UUID.randomUUID();
    final UUID schoolId = UUID.randomUUID();
    final UUID studentId = UUID.randomUUID();
    final UUID membershipId = UUID.randomUUID();
    final UUID projectId = UUID.randomUUID();
    final UUID activityProjectId = UUID.randomUUID();
    final UUID participantId = UUID.randomUUID();
    final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ActivityParticipantService(
                activityRepo,
                projectPort,
                participantPort,
                projectParticipantPort,
                participantQueryPort,
                membershipPort,
                scoreAttemptRepo,
                userRepo);
    }

    @Test
    void activeStudentCanJoinActivity() {
        givenActivity(ExecutionStatus.DRAFT);
        when(membershipPort.findActiveStudentMembershipId(studentId, schoolId))
                .thenReturn(Optional.of(membershipId));
        when(participantPort.existsByActivityAndMembership(activityId, membershipId))
                .thenReturn(false);
        var expected = participantRecord();
        when(participantPort.add(activityId, membershipId)).thenReturn(expected);

        var actual = service.addParticipant(activityId, studentId);

        assertThat(actual).isEqualTo(expected);
        verify(participantPort).add(activityId, membershipId);
    }

    @Test
    void studentFromOtherSchoolIsRejected() {
        givenActivity(ExecutionStatus.DRAFT);
        when(membershipPort.findActiveStudentMembershipId(studentId, schoolId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addParticipant(activityId, studentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active STUDENT");

        verify(participantPort, never()).add(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inactiveStudentMembershipIsRejected() {
        givenActivity(ExecutionStatus.DRAFT);
        when(membershipPort.findActiveStudentMembershipId(studentId, schoolId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addParticipant(activityId, studentId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(participantPort, never()).add(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateActivityParticipantIsRejected() {
        givenActivity(ExecutionStatus.DRAFT);
        givenMembership();
        when(participantPort.existsByActivityAndMembership(activityId, membershipId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addParticipant(activityId, studentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already");

        verify(participantPort, never()).add(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void terminalActivityRejectsParticipantAddition() {
        givenActivity(ExecutionStatus.ENDED);

        assertThatThrownBy(() -> service.addParticipant(activityId, studentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENDED");

        verify(participantPort, never()).add(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void participantWithProjectAssignmentCannotBeRemoved() {
        givenActivity(ExecutionStatus.DRAFT);
        givenMembership();
        when(participantPort.findByActivityAndMembership(activityId, membershipId))
                .thenReturn(Optional.of(participantRecord()));
        when(projectParticipantPort.existsByParticipantId(participantId)).thenReturn(true);

        assertThatThrownBy(() -> service.removeParticipant(activityId, studentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("project assignments");

        verify(participantPort, never()).remove(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void participantWithScoreAttemptCannotBeRemoved() {
        givenActivity(ExecutionStatus.DRAFT);
        givenMembership();
        when(participantPort.findByActivityAndMembership(activityId, membershipId))
                .thenReturn(Optional.of(participantRecord()));
        when(projectParticipantPort.existsByParticipantId(participantId)).thenReturn(false);
        when(projectPort.findByActivity(activityId)).thenReturn(List.of(projectRecord()));
        when(scoreAttemptRepo.existsByActivityProjectIdAndStudentId(activityProjectId, studentId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.removeParticipant(activityId, studentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score attempts");

        verify(participantPort, never()).remove(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activityParticipantCanBeAssignedToConfiguredProject() {
        givenActivity(ExecutionStatus.PUBLISHED);
        givenProject();
        givenMembership();
        when(participantPort.findByActivityAndMembership(activityId, membershipId))
                .thenReturn(Optional.of(participantRecord()));
        when(projectParticipantPort.existsByProjectAndParticipant(activityProjectId, participantId))
                .thenReturn(false);
        var expected = new ActivityProjectParticipantPort.ProjectParticipantRecord(
                UUID.randomUUID(), activityProjectId, participantId, actorId, Instant.now());
        when(projectParticipantPort.assign(activityProjectId, participantId, actorId))
                .thenReturn(expected);

        var actual = service.assignToProject(activityId, projectId, studentId, actorId);

        assertThat(actual).isEqualTo(expected);
        verify(projectParticipantPort).assign(activityProjectId, participantId, actorId);
    }

    @Test
    void participantNotInActivityCannotBeAssigned() {
        givenActivity(ExecutionStatus.DRAFT);
        givenProject();
        givenMembership();
        when(participantPort.findByActivityAndMembership(activityId, membershipId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignToProject(activityId, projectId, studentId, actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in this activity");

        verify(projectParticipantPort, never()).assign(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateProjectAssignmentIsRejected() {
        givenActivity(ExecutionStatus.DRAFT);
        givenProject();
        givenMembership();
        when(participantPort.findByActivityAndMembership(activityId, membershipId))
                .thenReturn(Optional.of(participantRecord()));
        when(projectParticipantPort.existsByProjectAndParticipant(activityProjectId, participantId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.assignToProject(activityId, projectId, studentId, actorId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already assigned");

        verify(projectParticipantPort, never()).assign(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void projectFromAnotherActivityIsRejected() {
        givenActivity(ExecutionStatus.DRAFT);
        when(projectPort.findByActivityAndProject(activityId, projectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignToProject(activityId, projectId, studentId, actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not configured");

        verify(projectParticipantPort, never()).assign(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void projectParticipantWithScoreAttemptCannotBeUnassigned() {
        givenUnassignableParticipant();
        when(scoreAttemptRepo.existsByActivityProjectIdAndStudentId(activityProjectId, studentId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.unassignFromProject(activityId, projectId, studentId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score attempts");

        verify(projectParticipantPort, never()).unassign(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void projectParticipantWithoutScoreCanBeUnassigned() {
        givenUnassignableParticipant();
        when(scoreAttemptRepo.existsByActivityProjectIdAndStudentId(activityProjectId, studentId))
                .thenReturn(false);

        service.unassignFromProject(activityId, projectId, studentId);

        verify(projectParticipantPort).unassign(activityProjectId, participantId);
    }

    private void givenUnassignableParticipant() {
        givenActivity(ExecutionStatus.IN_PROGRESS);
        givenProject();
        givenMembership();
        when(participantPort.findByActivityAndMembership(activityId, membershipId))
                .thenReturn(Optional.of(participantRecord()));
        when(projectParticipantPort.existsByProjectAndParticipant(activityProjectId, participantId))
                .thenReturn(true);
    }

    private void givenMembership() {
        when(membershipPort.findActiveStudentMembershipId(studentId, schoolId))
                .thenReturn(Optional.of(membershipId));
    }

    private void givenProject() {
        when(projectPort.findByActivityAndProject(activityId, projectId))
                .thenReturn(Optional.of(projectRecord()));
    }

    private void givenActivity(ExecutionStatus status) {
        var activity = Activity.reconstitute(new Activity.Builder()
                .id(new ActivityId(activityId))
                .schoolId(schoolId)
                .title("Activity")
                .createdBy(actorId)
                .executionStatus(status)
                .publicStatus(PublicStatus.NOT_SUBMITTED));
        when(activityRepo.findById(new ActivityId(activityId))).thenReturn(Optional.of(activity));
    }

    private ActivityParticipantPort.ParticipantRecord participantRecord() {
        return new ActivityParticipantPort.ParticipantRecord(
                participantId, activityId, membershipId, Instant.now());
    }

    private ActivityProjectPort.ProjectRecord projectRecord() {
        return new ActivityProjectPort.ProjectRecord(activityProjectId, activityId, projectId);
    }
}
