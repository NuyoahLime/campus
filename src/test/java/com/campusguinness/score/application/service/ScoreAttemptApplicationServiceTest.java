package com.campusguinness.score.application.service;

import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.application.query.model.ParticipantListResult;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.activity.internal.domain.PublicStatus;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.score.application.command.SubmitScoreCommand;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAttemptApplicationServiceTest {
    @Mock ScoreAttemptRepository repo;
    @Mock ActivityRepository activityRepo;
    @Mock ActivityProjectPort projectPort;
    @Mock ResponsibleTeacherPort teacherPort;
    @Mock ActivityProjectParticipantPort projectParticipantPort;
    @Mock ActivityParticipantQueryPort participantQueryPort;
    @Mock SchoolMembershipQueryPort membershipPort;
    ScoreAttemptApplicationService svc;

    private final UUID schoolId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final UUID activityProjectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID teacherMembershipId = UUID.randomUUID();
    private final UUID studentMembershipId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ScoreAttemptApplicationService(repo, activityRepo, projectPort, teacherPort,
                projectParticipantPort, participantQueryPort, membershipPort);
    }

    private void stubHappyPath() {
        var apRecord = new ActivityProjectPort.ProjectRecord(activityProjectId, activityId, projectId);
        when(projectPort.findById(activityProjectId)).thenReturn(Optional.of(apRecord));

        var activity = Activity.reconstitute(new Activity.Builder()
                .id(new ActivityId(activityId)).schoolId(schoolId).createdBy(UUID.randomUUID())
                .title("Test").executionStatus(ExecutionStatus.PUBLISHED).publicStatus(PublicStatus.NOT_SUBMITTED));
        when(activityRepo.findById(any())).thenReturn(Optional.of(activity));

        when(membershipPort.findActiveTeacherMembershipId(actorId, schoolId))
                .thenReturn(Optional.of(teacherMembershipId));
        when(teacherPort.exists(activityProjectId, teacherMembershipId)).thenReturn(true);

        when(membershipPort.findActiveStudentMembershipId(studentId, schoolId))
                .thenReturn(Optional.of(studentMembershipId));

        var participantResult = new ParticipantListResult(participantId, activityId, studentMembershipId,
                studentId, null, null, null, null, 0, false, Instant.now());
        when(participantQueryPort.findByActivityAndMemberships(activityId, List.of(studentMembershipId)))
                .thenReturn(Optional.of(participantResult));

        when(projectParticipantPort.existsByProjectAndParticipant(activityProjectId, participantId))
                .thenReturn(true);
    }

    private SubmitScoreCommand cmd() {
        return new SubmitScoreCommand(UUID.randomUUID(), activityProjectId, studentId,
                1, ScoreStorageType.INTEGER, new ScoreValue.IntegerScore(100),
                Instant.now(), "teacher", actorId);
    }

    @Nested
    class Submit {
        @Test void shouldSubmitWhenAssignedTeacher() {
            stubHappyPath();
            var r = svc.submit(cmd());
            assertThat(r.status()).isEqualTo("PENDING_REVIEW");
            verify(repo).save(any());
        }

        @Test void shouldDenyWhenActivityProjectNotFound() {
            when(projectPort.findById(activityProjectId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.submit(cmd()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ActivityProject not found");
            verify(repo, never()).save(any());
        }

        @Test void shouldDenyWhenNotActiveTeacher() {
            var apRecord = new ActivityProjectPort.ProjectRecord(activityProjectId, activityId, projectId);
            when(projectPort.findById(activityProjectId)).thenReturn(Optional.of(apRecord));
            var activity = Activity.reconstitute(new Activity.Builder()
                    .id(new ActivityId(activityId)).schoolId(schoolId).createdBy(UUID.randomUUID())
                    .title("Test").executionStatus(ExecutionStatus.PUBLISHED).publicStatus(PublicStatus.NOT_SUBMITTED));
            when(activityRepo.findById(any())).thenReturn(Optional.of(activity));
            when(membershipPort.findActiveTeacherMembershipId(actorId, schoolId))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.submit(cmd()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test void shouldDenyWhenNotAssignedTeacher() {
            var apRecord = new ActivityProjectPort.ProjectRecord(activityProjectId, activityId, projectId);
            when(projectPort.findById(activityProjectId)).thenReturn(Optional.of(apRecord));
            var activity = Activity.reconstitute(new Activity.Builder()
                    .id(new ActivityId(activityId)).schoolId(schoolId).createdBy(UUID.randomUUID())
                    .title("Test").executionStatus(ExecutionStatus.PUBLISHED).publicStatus(PublicStatus.NOT_SUBMITTED));
            when(activityRepo.findById(any())).thenReturn(Optional.of(activity));
            when(membershipPort.findActiveTeacherMembershipId(actorId, schoolId))
                    .thenReturn(Optional.of(teacherMembershipId));
            when(teacherPort.exists(activityProjectId, teacherMembershipId)).thenReturn(false);
            assertThatThrownBy(() -> svc.submit(cmd()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test void shouldDenyWhenStudentNotInActivityRoster() {
            var apRecord = new ActivityProjectPort.ProjectRecord(activityProjectId, activityId, projectId);
            when(projectPort.findById(activityProjectId)).thenReturn(Optional.of(apRecord));
            var activity = Activity.reconstitute(new Activity.Builder()
                    .id(new ActivityId(activityId)).schoolId(schoolId).createdBy(UUID.randomUUID())
                    .title("Test").executionStatus(ExecutionStatus.PUBLISHED).publicStatus(PublicStatus.NOT_SUBMITTED));
            when(activityRepo.findById(any())).thenReturn(Optional.of(activity));
            when(membershipPort.findActiveTeacherMembershipId(actorId, schoolId))
                    .thenReturn(Optional.of(teacherMembershipId));
            when(teacherPort.exists(activityProjectId, teacherMembershipId)).thenReturn(true);
            when(membershipPort.findActiveStudentMembershipId(studentId, schoolId))
                    .thenReturn(Optional.of(studentMembershipId));
            when(participantQueryPort.findByActivityAndMemberships(activityId, List.of(studentMembershipId)))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.submit(cmd()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("not in the activity participant roster");
            verify(repo, never()).save(any());
        }

        @Test void shouldDenyWhenStudentNotAssignedToProject() {
            var apRecord = new ActivityProjectPort.ProjectRecord(activityProjectId, activityId, projectId);
            when(projectPort.findById(activityProjectId)).thenReturn(Optional.of(apRecord));
            var activity = Activity.reconstitute(new Activity.Builder()
                    .id(new ActivityId(activityId)).schoolId(schoolId).createdBy(UUID.randomUUID())
                    .title("Test").executionStatus(ExecutionStatus.PUBLISHED).publicStatus(PublicStatus.NOT_SUBMITTED));
            when(activityRepo.findById(any())).thenReturn(Optional.of(activity));
            when(membershipPort.findActiveTeacherMembershipId(actorId, schoolId))
                    .thenReturn(Optional.of(teacherMembershipId));
            when(teacherPort.exists(activityProjectId, teacherMembershipId)).thenReturn(true);
            when(membershipPort.findActiveStudentMembershipId(studentId, schoolId))
                    .thenReturn(Optional.of(studentMembershipId));
            var participantResult = new ParticipantListResult(participantId, activityId, studentMembershipId,
                    studentId, null, null, null, null, 0, false, Instant.now());
            when(participantQueryPort.findByActivityAndMemberships(activityId, List.of(studentMembershipId)))
                    .thenReturn(Optional.of(participantResult));
            when(projectParticipantPort.existsByProjectAndParticipant(activityProjectId, participantId))
                    .thenReturn(false);
            assertThatThrownBy(() -> svc.submit(cmd()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("not assigned to this activity project");
            verify(repo, never()).save(any());
        }
    }

    @Nested
    class FindMyApprovedScores {
        @Test void shouldReturnOnlyApproved() {
            UUID studentId = UUID.randomUUID();
            var s = approved(studentId);
            when(repo.findApprovedByStudentId(studentId)).thenReturn(List.of(s));
            var results = svc.findMyApprovedScores(studentId);
            assertThat(results).hasSize(1);
        }
    }

    @Nested
    class GetMyApprovedScore {
        @Test void shouldReturnDetail() {
            UUID sid = UUID.randomUUID(), aid = UUID.randomUUID();
            var s = approved(sid);
            when(repo.findApprovedByIdAndStudentId(aid, sid)).thenReturn(Optional.of(s));
            assertThat(svc.getMyApprovedScore(aid, sid).status()).isEqualTo("APPROVED");
        }
        @Test void shouldThrowWhenNotFound() {
            when(repo.findApprovedByIdAndStudentId(any(), any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.getMyApprovedScore(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class GetMyProgress {
        @Test void shouldUseDatabaseOwnerFilter() {
            UUID sid = UUID.randomUUID(), aid = UUID.randomUUID();
            var s = approved(sid);
            when(repo.findByIdAndStudentId(aid, sid)).thenReturn(Optional.of(s));
            var r = svc.getMyProgress(aid, sid);
            assertThat(r.status()).isEqualTo("APPROVED");
            verify(repo).findByIdAndStudentId(aid, sid);
        }
        @Test void shouldReturn404ForOthersScore() {
            when(repo.findByIdAndStudentId(any(), any())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> svc.getMyProgress(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ScoreAttempt not found");
        }
    }

    private ScoreAttempt approved(UUID studentId) {
        var s = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID())).schoolId(schoolId)
                .activityProjectId(activityProjectId).studentId(studentId)
                .attemptNumber(1).scoreStorageType(ScoreStorageType.INTEGER)
                .scoreValue(new ScoreValue.IntegerScore(100))
                .scoreBusinessTime(Instant.now()).timeSource("teacher").enteredBy(actorId));
        s.submit();
        s.approve();
        return s;
    }
}
