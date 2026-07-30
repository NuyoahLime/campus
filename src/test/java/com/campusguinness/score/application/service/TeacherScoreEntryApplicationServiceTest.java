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
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.internal.domain.ChallengeProject;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import com.campusguinness.project.internal.domain.ComparisonDirection;
import com.campusguinness.project.internal.domain.ProjectCategory;
import com.campusguinness.project.internal.domain.ProjectName;
import com.campusguinness.project.internal.domain.ProjectStatus;
import com.campusguinness.project.internal.domain.ScoreConfig;
import com.campusguinness.project.internal.domain.ScoreIndicatorType;
import com.campusguinness.score.application.command.CreateTeacherScoreCommand;
import com.campusguinness.score.application.command.UpdateTeacherScoreCommand;
import com.campusguinness.score.application.exception.ScoreEntryConflictException;
import com.campusguinness.score.application.exception.ScoreEntryNotFoundException;
import com.campusguinness.score.application.port.ScoreAttemptNumberAllocatorPort;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.internal.domain.AttemptStatus;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherScoreEntryApplicationServiceTest {
    @Mock ScoreAttemptRepository attempts;
    @Mock ActivityProjectPort activityProjects;
    @Mock ActivityRepository activities;
    @Mock ChallengeProjectRepository projects;
    @Mock SchoolMembershipQueryPort memberships;
    @Mock ResponsibleTeacherPort responsibleTeachers;
    @Mock ActivityParticipantQueryPort activityParticipants;
    @Mock ActivityProjectParticipantPort projectParticipants;
    @Mock ScoreAttemptNumberAllocatorPort attemptNumbers;

    private TeacherScoreEntryApplicationService service;
    private UUID actorId;
    private UUID otherTeacherId;
    private UUID schoolId;
    private UUID otherSchoolId;
    private UUID activityId;
    private UUID activityProjectId;
    private UUID projectId;
    private UUID studentId;
    private UUID teacherMembershipId;
    private UUID studentMembershipId;
    private UUID participantId;
    private Instant businessTime;

    @BeforeEach
    void setUp() {
        service = new TeacherScoreEntryApplicationService(
                attempts, activityProjects, activities, projects, memberships,
                responsibleTeachers, activityParticipants, projectParticipants,
                attemptNumbers);
        actorId = UUID.randomUUID();
        otherTeacherId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        otherSchoolId = UUID.randomUUID();
        activityId = UUID.randomUUID();
        activityProjectId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        teacherMembershipId = UUID.randomUUID();
        studentMembershipId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        businessTime = Instant.parse("2026-07-30T10:00:00Z");

        lenient().when(activityProjects.findById(activityProjectId))
                .thenReturn(Optional.of(new ActivityProjectPort.ProjectRecord(
                        activityProjectId, activityId, projectId)));
        lenient().when(activities.findById(new ActivityId(activityId)))
                .thenReturn(Optional.of(activity(ExecutionStatus.PUBLISHED, schoolId)));
        lenient().when(memberships.findActiveTeacherMembershipId(actorId, schoolId))
                .thenReturn(Optional.of(teacherMembershipId));
        lenient().when(responsibleTeachers.exists(
                activityProjectId, teacherMembershipId)).thenReturn(true);
        lenient().when(projects.findById(new ChallengeProjectId(projectId)))
                .thenReturn(Optional.of(project(integerConfig())));
        lenient().when(memberships.findActiveStudentMembershipId(studentId, schoolId))
                .thenReturn(Optional.of(studentMembershipId));
        lenient().when(activityParticipants.findByActivityAndMemberships(
                eq(activityId), anyList())).thenReturn(Optional.of(participant()));
        lenient().when(projectParticipants.existsByProjectAndParticipant(
                activityProjectId, participantId)).thenReturn(true);
        lenient().when(memberships.existsOtherActiveSchoolAdmin(schoolId, actorId))
                .thenReturn(true);
        lenient().when(attemptNumbers.allocateNext(
                activityProjectId, participantId, studentId)).thenReturn(1);
    }

    @Test
    void responsibleTeacherCanSubmitScore() {
        service.createAndSubmit(actorId, integerCreate(100L));

        ScoreAttempt saved = captureSaved();
        assertThat(saved.enteredBy()).isEqualTo(actorId);
        assertThat(saved.schoolId()).isEqualTo(schoolId);
        assertThat(saved.status()).isEqualTo(AttemptStatus.PENDING_REVIEW);
    }

    @Test
    void unassignedTeacherCannotSubmitScore() {
        when(responsibleTeachers.exists(
                activityProjectId, teacherMembershipId)).thenReturn(false);

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryNotFoundException.class);
    }

    @Test
    void inactiveTeacherCannotSubmitScore() {
        when(memberships.findActiveTeacherMembershipId(actorId, schoolId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryNotFoundException.class);
    }

    @Test
    void studentFromOtherSchoolIsRejected() {
        when(memberships.findActiveStudentMembershipId(studentId, schoolId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryNotFoundException.class);
    }

    @Test
    void studentNotInActivityRosterIsRejected() {
        when(activityParticipants.findByActivityAndMemberships(
                eq(activityId), anyList())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryConflictException.class);
    }

    @Test
    void studentNotAssignedToProjectIsRejected() {
        when(projectParticipants.existsByProjectAndParticipant(
                activityProjectId, participantId)).thenReturn(false);

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryConflictException.class);
    }

    @Test
    void endedActivityRejectsScore() {
        when(activities.findById(new ActivityId(activityId)))
                .thenReturn(Optional.of(activity(ExecutionStatus.ENDED, schoolId)));

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryConflictException.class);
    }

    @Test
    void cancelledActivityRejectsScore() {
        when(activities.findById(new ActivityId(activityId)))
                .thenReturn(Optional.of(activity(ExecutionStatus.CANCELLED, schoolId)));

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryConflictException.class);
    }

    @Test
    void serverDeterminesStorageType() {
        when(projects.findById(new ChallengeProjectId(projectId)))
                .thenReturn(Optional.of(project(decimalConfig(2))));

        service.createAndSubmit(actorId, decimalCreate(new BigDecimal("12.34")));

        assertThat(captureSaved().scoreStorageType())
                .isEqualTo(ScoreStorageType.DECIMAL);
    }

    @Test
    void serverAllocatesAttemptNumber() {
        service.createAndSubmit(actorId, integerCreate(100L));

        verify(attemptNumbers).allocateNext(
                activityProjectId, participantId, studentId);
    }

    @Test
    void firstAttemptNumberIsOne() {
        service.createAndSubmit(actorId, integerCreate(100L));

        assertThat(captureSaved().attemptNumber()).isEqualTo(1);
    }

    @Test
    void nextAttemptNumberIsIncremented() {
        when(attemptNumbers.allocateNext(
                activityProjectId, participantId, studentId)).thenReturn(4);

        service.createAndSubmit(actorId, integerCreate(100L));

        assertThat(captureSaved().attemptNumber()).isEqualTo(4);
    }

    @Test
    void integerValidationWorks() {
        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(-1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decimalScaleValidationWorks() {
        when(projects.findById(new ChallengeProjectId(projectId)))
                .thenReturn(Optional.of(project(decimalConfig(2))));

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, decimalCreate(new BigDecimal("1.234"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroDurationIsAccepted() {
        when(projects.findById(new ChallengeProjectId(projectId)))
                .thenReturn(Optional.of(project(durationConfig())));

        service.createAndSubmit(actorId, durationCreate(0L));

        assertThat(captureSaved().scoreValue())
                .isEqualTo(new ScoreValue.DurationScore(0L));
    }

    @Test
    void gradeMustMatchGradeOrder() {
        when(projects.findById(new ChallengeProjectId(projectId)))
                .thenReturn(Optional.of(project(gradeConfig("A,B,C"))));

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, gradeCreate("D")))
                .isInstanceOf(IllegalArgumentException.class);
        service.createAndSubmit(actorId, gradeCreate("B"));
        assertThat(captureSaved().scoreValue())
                .isEqualTo(new ScoreValue.GradeScore("B"));
    }

    @Test
    void businessTimeIsRequired() {
        var command = new CreateTeacherScoreCommand(
                activityProjectId, studentId, 100L, null, null, null,
                null, "ON_SITE_RECORD");

        assertThatThrownBy(() -> service.createAndSubmit(actorId, command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeSourceIsRequired() {
        var command = new CreateTeacherScoreCommand(
                activityProjectId, studentId, 100L, null, null, null,
                businessTime, " ");

        assertThatThrownBy(() -> service.createAndSubmit(actorId, command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newScoreIsPendingReview() {
        service.createAndSubmit(actorId, integerCreate(100L));

        assertThat(captureSaved().status()).isEqualTo(AttemptStatus.PENDING_REVIEW);
    }

    @Test
    void newScoreIsNotCurrentEffective() {
        service.createAndSubmit(actorId, integerCreate(100L));

        assertThat(captureSaved().isCurrentEffective()).isFalse();
    }

    @Test
    void rejectedScoreCanReturnToDraft() {
        ScoreAttempt rejected = attempt(AttemptStatus.REJECTED, actorId);
        when(attempts.findByIdForUpdate(rejected.id()))
                .thenReturn(Optional.of(rejected));

        service.updateDraft(
                actorId, rejected.id().value(), integerUpdate(120L));

        assertThat(rejected.status()).isEqualTo(AttemptStatus.DRAFT);
        assertThat(rejected.scoreValue())
                .isEqualTo(new ScoreValue.IntegerScore(120L));
    }

    @Test
    void reviewHistoryIsPreserved() {
        ScoreAttempt rejected = attempt(AttemptStatus.REJECTED, actorId);
        when(attempts.findByIdForUpdate(rejected.id()))
                .thenReturn(Optional.of(rejected));

        service.updateDraft(
                actorId, rejected.id().value(), integerUpdate(120L));

        verify(attempts).save(rejected);
        assertThat(rejected.domainEvents()).extracting(
                event -> event.getClass().getSimpleName())
                .containsExactly("ScoreAttemptReturnedToDraft");
    }

    @Test
    void teacherCanSubmitOwnDraft() {
        ScoreAttempt draft = attempt(AttemptStatus.DRAFT, actorId);
        when(attempts.findByIdForUpdate(draft.id())).thenReturn(Optional.of(draft));

        service.submitDraft(actorId, draft.id().value());

        assertThat(draft.status()).isEqualTo(AttemptStatus.PENDING_REVIEW);
    }

    @Test
    void teacherCannotModifyOtherTeacherScore() {
        ScoreAttempt draft = attempt(AttemptStatus.DRAFT, actorId);
        when(attempts.findByIdForUpdate(draft.id())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.updateDraft(
                otherTeacherId, draft.id().value(), integerUpdate(120L)))
                .isInstanceOf(ScoreEntryNotFoundException.class);
    }

    @Test
    void pendingScoreCannotBeEdited() {
        ScoreAttempt pending = attempt(AttemptStatus.PENDING_REVIEW, actorId);
        when(attempts.findByIdForUpdate(pending.id()))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.updateDraft(
                actorId, pending.id().value(), integerUpdate(120L)))
                .isInstanceOf(ScoreEntryConflictException.class);
    }

    @Test
    void approvedScoreCannotBeEdited() {
        ScoreAttempt approved = attempt(AttemptStatus.APPROVED, actorId);
        when(attempts.findByIdForUpdate(approved.id()))
                .thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.updateDraft(
                actorId, approved.id().value(), integerUpdate(120L)))
                .isInstanceOf(ScoreEntryConflictException.class);
    }

    @Test
    void submitRevalidatesResponsibleAssignment() {
        ScoreAttempt draft = attempt(AttemptStatus.DRAFT, actorId);
        when(attempts.findByIdForUpdate(draft.id())).thenReturn(Optional.of(draft));
        when(responsibleTeachers.exists(
                activityProjectId, teacherMembershipId)).thenReturn(false);

        assertThatThrownBy(() -> service.submitDraft(actorId, draft.id().value()))
                .isInstanceOf(ScoreEntryNotFoundException.class);
        assertThat(draft.status()).isEqualTo(AttemptStatus.DRAFT);
    }

    @Test
    void submitRevalidatesParticipantAssignment() {
        ScoreAttempt draft = attempt(AttemptStatus.DRAFT, actorId);
        when(attempts.findByIdForUpdate(draft.id())).thenReturn(Optional.of(draft));
        when(projectParticipants.existsByProjectAndParticipant(
                activityProjectId, participantId)).thenReturn(false);

        assertThatThrownBy(() -> service.submitDraft(actorId, draft.id().value()))
                .isInstanceOf(ScoreEntryConflictException.class);
        assertThat(draft.status()).isEqualTo(AttemptStatus.DRAFT);
    }

    @Test
    void noReviewerReturnsConflict() {
        when(memberships.existsOtherActiveSchoolAdmin(schoolId, actorId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryConflictException.class)
                .extracting("errorCode")
                .isEqualTo("NO_ELIGIBLE_SCORE_REVIEWER");
    }

    @Test
    void failedOperationDoesNotPersistScore() {
        when(activities.findById(new ActivityId(activityId)))
                .thenReturn(Optional.of(activity(
                        ExecutionStatus.PUBLISHED, otherSchoolId)));

        assertThatThrownBy(() -> service.createAndSubmit(
                actorId, integerCreate(100L)))
                .isInstanceOf(ScoreEntryNotFoundException.class);
        verify(attempts, never()).save(any());
    }

    private ScoreAttempt captureSaved() {
        ArgumentCaptor<ScoreAttempt> captor =
                ArgumentCaptor.forClass(ScoreAttempt.class);
        verify(attempts).save(captor.capture());
        return captor.getValue();
    }

    private CreateTeacherScoreCommand integerCreate(Long value) {
        return new CreateTeacherScoreCommand(
                activityProjectId, studentId, value, null, null, null,
                businessTime, "ON_SITE_RECORD");
    }

    private CreateTeacherScoreCommand decimalCreate(BigDecimal value) {
        return new CreateTeacherScoreCommand(
                activityProjectId, studentId, null, value, null, null,
                businessTime, "ON_SITE_RECORD");
    }

    private CreateTeacherScoreCommand durationCreate(Long value) {
        return new CreateTeacherScoreCommand(
                activityProjectId, studentId, null, null, value, null,
                businessTime, "ON_SITE_RECORD");
    }

    private CreateTeacherScoreCommand gradeCreate(String value) {
        return new CreateTeacherScoreCommand(
                activityProjectId, studentId, null, null, null, value,
                businessTime, "ON_SITE_RECORD");
    }

    private UpdateTeacherScoreCommand integerUpdate(Long value) {
        return new UpdateTeacherScoreCommand(
                value, null, null, null, businessTime, "ON_SITE_RECORD");
    }

    private ScoreAttempt attempt(AttemptStatus status, UUID enteredBy) {
        return ScoreAttempt.reconstitute(new ScoreAttempt.Builder()
                        .id(new ScoreAttemptId(UUID.randomUUID()))
                        .schoolId(schoolId)
                        .activityProjectId(activityProjectId)
                        .studentId(studentId)
                        .attemptNumber(1)
                        .scoreStorageType(ScoreStorageType.INTEGER)
                        .scoreValue(new ScoreValue.IntegerScore(100L))
                        .scoreBusinessTime(businessTime)
                        .timeSource("ON_SITE_RECORD")
                        .enteredBy(enteredBy),
                status,
                false,
                status == AttemptStatus.DRAFT ? null : businessTime,
                false);
    }

    private Activity activity(
            ExecutionStatus status, UUID activitySchoolId) {
        return Activity.reconstitute(new Activity.Builder()
                .id(new ActivityId(activityId))
                .schoolId(activitySchoolId)
                .title("Teacher Score Activity")
                .createdBy(actorId)
                .executionStatus(status)
                .publicStatus(PublicStatus.NOT_SUBMITTED));
    }

    private ParticipantListResult participant() {
        return new ParticipantListResult(
                participantId, activityId, studentMembershipId, studentId,
                "student", "6", "1", "S001", 1, false, businessTime);
    }

    private ChallengeProject project(ScoreConfig config) {
        return ChallengeProject.reconstitute(
                new ChallengeProjectId(projectId),
                new ProjectName("Teacher Score Project"),
                new ProjectCategory("SPORT"),
                config, null, null, null, ProjectStatus.PUBLISHED);
    }

    private static ScoreConfig integerConfig() {
        return new ScoreConfig(
                com.campusguinness.project.internal.domain.ScoreStorageType.INTEGER,
                ScoreIndicatorType.NUMERIC,
                ComparisonDirection.HIGHER_BETTER,
                "points", 0, "BEST", null, null, true);
    }

    private static ScoreConfig decimalConfig(int decimalPlaces) {
        return new ScoreConfig(
                com.campusguinness.project.internal.domain.ScoreStorageType.DECIMAL,
                ScoreIndicatorType.NUMERIC,
                ComparisonDirection.HIGHER_BETTER,
                "points", decimalPlaces, "BEST", null, null, true);
    }

    private static ScoreConfig durationConfig() {
        return new ScoreConfig(
                com.campusguinness.project.internal.domain.ScoreStorageType.DURATION,
                ScoreIndicatorType.DURATION_MS,
                ComparisonDirection.LOWER_BETTER,
                "ms", null, "BEST", null, null, true);
    }

    private static ScoreConfig gradeConfig(String gradeOrder) {
        return new ScoreConfig(
                com.campusguinness.project.internal.domain.ScoreStorageType.GRADE,
                ScoreIndicatorType.GRADE_LEVEL,
                ComparisonDirection.GRADE_ORDER,
                null, null, "BEST", gradeOrder, null, true);
    }
}
