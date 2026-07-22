package com.campusguinness.score.application.service;

import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.activity.internal.domain.PublicStatus;
import com.campusguinness.score.application.command.SubmitScoreCommand;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.internal.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreAttemptApplicationServiceTest {
    @Mock ScoreAttemptRepository repo;
    @Mock ActivityRepository activityRepo;
    @Mock ActivityProjectPort projectPort;
    @Mock ResponsibleTeacherPort teacherPort;
    @Mock JdbcTemplate jdbc;
    ScoreAttemptApplicationService svc;

    private final UUID schoolId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final UUID activityProjectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ScoreAttemptApplicationService(repo, activityRepo, projectPort, teacherPort, jdbc);
    }

    private void stubHappyPath() {
        var apRecord = new ActivityProjectPort.ProjectRecord(activityProjectId, activityId, projectId);
        when(projectPort.findById(activityProjectId)).thenReturn(Optional.of(apRecord));

        var activity = Activity.reconstitute(new Activity.Builder()
                .id(new ActivityId(activityId)).schoolId(schoolId).createdBy(UUID.randomUUID())
                .title("Test").executionStatus(ExecutionStatus.PUBLISHED).publicStatus(PublicStatus.NOT_SUBMITTED));
        when(activityRepo.findById(any())).thenReturn(Optional.of(activity));

        when(jdbc.queryForList(anyString(), eq(UUID.class), eq(actorId), eq(schoolId)))
                .thenReturn(List.of(membershipId));

        when(teacherPort.exists(activityProjectId, membershipId)).thenReturn(true);
    }

    private SubmitScoreCommand cmd() {
        return new SubmitScoreCommand(UUID.randomUUID(), activityProjectId, UUID.randomUUID(),
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
            when(jdbc.queryForList(anyString(), eq(UUID.class), eq(actorId), eq(schoolId)))
                    .thenReturn(List.of());
            assertThatThrownBy(() -> svc.submit(cmd()))
                    .isInstanceOf(AccessDeniedException.class);
            verify(repo, never()).save(any());
        }

        @Test void shouldDenyWhenNotAssignedTeacher() {
            var apRecord = new ActivityProjectPort.ProjectRecord(activityProjectId, activityId, projectId);
            when(projectPort.findById(activityProjectId)).thenReturn(Optional.of(apRecord));
            var activity = Activity.reconstitute(new Activity.Builder()
                    .id(new ActivityId(activityId)).schoolId(schoolId).createdBy(UUID.randomUUID())
                    .title("Test").executionStatus(ExecutionStatus.PUBLISHED).publicStatus(PublicStatus.NOT_SUBMITTED));
            when(activityRepo.findById(any())).thenReturn(Optional.of(activity));
            when(jdbc.queryForList(anyString(), eq(UUID.class), eq(actorId), eq(schoolId)))
                    .thenReturn(List.of(membershipId));
            when(teacherPort.exists(activityProjectId, membershipId)).thenReturn(false);
            assertThatThrownBy(() -> svc.submit(cmd()))
                    .isInstanceOf(AccessDeniedException.class);
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
