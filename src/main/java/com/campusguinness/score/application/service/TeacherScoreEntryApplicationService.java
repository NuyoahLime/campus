package com.campusguinness.score.application.service;

import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.internal.domain.ChallengeProject;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import com.campusguinness.project.internal.domain.ScoreConfig;
import com.campusguinness.score.application.command.CreateTeacherScoreCommand;
import com.campusguinness.score.application.command.UpdateTeacherScoreCommand;
import com.campusguinness.score.application.exception.ScoreEntryConfigurationException;
import com.campusguinness.score.application.exception.ScoreEntryConflictException;
import com.campusguinness.score.application.exception.ScoreEntryNotFoundException;
import com.campusguinness.score.application.port.ScoreAttemptNumberAllocatorPort;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.internal.domain.AttemptStatus;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TeacherScoreEntryApplicationService {
    private final ScoreAttemptRepository attempts;
    private final ActivityProjectPort activityProjects;
    private final ActivityRepository activities;
    private final ChallengeProjectRepository projects;
    private final SchoolMembershipQueryPort memberships;
    private final ResponsibleTeacherPort responsibleTeachers;
    private final ActivityParticipantQueryPort activityParticipants;
    private final ActivityProjectParticipantPort projectParticipants;
    private final ScoreAttemptNumberAllocatorPort attemptNumbers;

    public TeacherScoreEntryApplicationService(
            ScoreAttemptRepository attempts,
            ActivityProjectPort activityProjects,
            ActivityRepository activities,
            ChallengeProjectRepository projects,
            SchoolMembershipQueryPort memberships,
            ResponsibleTeacherPort responsibleTeachers,
            ActivityParticipantQueryPort activityParticipants,
            ActivityProjectParticipantPort projectParticipants,
            ScoreAttemptNumberAllocatorPort attemptNumbers) {
        this.attempts = attempts;
        this.activityProjects = activityProjects;
        this.activities = activities;
        this.projects = projects;
        this.memberships = memberships;
        this.responsibleTeachers = responsibleTeachers;
        this.activityParticipants = activityParticipants;
        this.projectParticipants = projectParticipants;
        this.attemptNumbers = attemptNumbers;
    }

    public UUID createAndSubmit(UUID actorId, CreateTeacherScoreCommand command) {
        return createAndSubmit(actorId, command, null);
    }

    public UUID createAndSubmitLegacy(
            UUID actorId,
            CreateTeacherScoreCommand command,
            String clientStorageType) {
        if (clientStorageType == null || clientStorageType.isBlank()) {
            throw new IllegalArgumentException("scoreStorageType is required");
        }
        return createAndSubmit(actorId, command, clientStorageType.trim());
    }

    private UUID createAndSubmit(
            UUID actorId,
            CreateTeacherScoreCommand command,
            String clientStorageType) {
        requireCreateCommand(command);
        EntryContext context = loadEntryContext(
                actorId, command.activityProjectId(), command.studentId());
        if (clientStorageType != null
                && !context.scoreConfig().storageType().name().equals(clientStorageType)) {
            throw new IllegalArgumentException(
                    "scoreStorageType does not match project configuration");
        }
        if (!memberships.existsOtherActiveSchoolAdmin(context.schoolId(), actorId)) {
            throw noReviewer();
        }
        int attemptNumber = attemptNumbers.allocateNext(
                command.activityProjectId(),
                context.activityParticipantId(),
                command.studentId());
        var fields = ScoreEntryValueFactory.create(
                context.scoreConfig(),
                command.integerValue(),
                command.decimalValue(),
                command.durationMs(),
                command.grade(),
                command.scoreBusinessTime(),
                command.timeSource());
        ScoreAttempt attempt = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID()))
                .schoolId(context.schoolId())
                .activityProjectId(command.activityProjectId())
                .studentId(command.studentId())
                .attemptNumber(attemptNumber)
                .scoreStorageType(ScoreEntryValueFactory.storageType(context.scoreConfig()))
                .scoreValue(fields.value())
                .scoreBusinessTime(fields.businessTime())
                .timeSource(fields.timeSource())
                .enteredBy(actorId)
                .replacesId(null)
                .manualMakeup(false));
        attempt.submit();
        attempts.save(attempt);
        return attempt.id().value();
    }

    public UUID updateDraft(
            UUID actorId,
            UUID attemptId,
            UpdateTeacherScoreCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("request body is required");
        }
        ScoreAttempt attempt = lockOwnedAttempt(actorId, attemptId);
        if (attempt.status() != AttemptStatus.DRAFT
                && attempt.status() != AttemptStatus.REJECTED) {
            throw new ScoreEntryConflictException(
                    "Only DRAFT or REJECTED scores can be edited");
        }
        EntryContext context = loadEntryContext(
                actorId, attempt.activityProjectId(), attempt.studentId());
        if (!attempt.schoolId().equals(context.schoolId())) {
            throw new ScoreEntryNotFoundException();
        }
        ScoreEntryValueFactory.ensureStoredTypeMatchesProject(
                attempt, context.scoreConfig());
        var fields = ScoreEntryValueFactory.create(
                context.scoreConfig(),
                command.integerValue(),
                command.decimalValue(),
                command.durationMs(),
                command.grade(),
                command.scoreBusinessTime(),
                command.timeSource());
        if (attempt.status() == AttemptStatus.REJECTED) {
            attempt.returnToDraft();
        }
        attempt.updateDraft(
                fields.value(), fields.businessTime(), fields.timeSource());
        attempts.save(attempt);
        return attempt.id().value();
    }

    public UUID submitDraft(UUID actorId, UUID attemptId) {
        ScoreAttempt attempt = lockOwnedAttempt(actorId, attemptId);
        if (attempt.status() != AttemptStatus.DRAFT) {
            throw new ScoreEntryConflictException(
                    "Only DRAFT scores can be submitted");
        }
        EntryContext context = loadEntryContext(
                actorId, attempt.activityProjectId(), attempt.studentId());
        if (!attempt.schoolId().equals(context.schoolId())) {
            throw new ScoreEntryNotFoundException();
        }
        ScoreEntryValueFactory.ensureStoredTypeMatchesProject(
                attempt, context.scoreConfig());
        ScoreEntryValueFactory.validate(
                attempt.scoreValue(),
                attempt.scoreBusinessTime(),
                attempt.timeSource(),
                context.scoreConfig());
        if (!memberships.existsOtherActiveSchoolAdmin(context.schoolId(), actorId)) {
            throw noReviewer();
        }
        attempt.submit();
        attempts.save(attempt);
        return attempt.id().value();
    }

    private ScoreAttempt lockOwnedAttempt(UUID actorId, UUID attemptId) {
        requireActor(actorId);
        if (attemptId == null) {
            throw new ScoreEntryNotFoundException();
        }
        ScoreAttempt attempt = attempts.findByIdForUpdate(new ScoreAttemptId(attemptId))
                .orElseThrow(ScoreEntryNotFoundException::new);
        if (!attempt.enteredBy().equals(actorId)) {
            throw new ScoreEntryNotFoundException();
        }
        return attempt;
    }

    private EntryContext loadEntryContext(
            UUID actorId, UUID activityProjectId, UUID studentId) {
        requireActor(actorId);
        var activityProject = activityProjects.findById(activityProjectId)
                .orElseThrow(ScoreEntryNotFoundException::new);
        Activity activity = activities.findById(
                        new ActivityId(activityProject.activityId()))
                .orElseThrow(ScoreEntryNotFoundException::new);
        UUID teacherMembershipId = memberships.findActiveTeacherMembershipId(
                        actorId, activity.schoolId())
                .orElseThrow(ScoreEntryNotFoundException::new);
        if (!responsibleTeachers.exists(activityProjectId, teacherMembershipId)) {
            throw new ScoreEntryNotFoundException();
        }
        if (activity.executionStatus() == ExecutionStatus.ENDED
                || activity.executionStatus() == ExecutionStatus.CANCELLED) {
            throw new ScoreEntryConflictException(
                    "Cannot enter scores for a terminal activity");
        }
        ChallengeProject project = projects.findById(
                        new ChallengeProjectId(activityProject.projectId()))
                .orElseThrow(() -> new ScoreEntryConfigurationException(
                        "Challenge project configuration is missing"));
        ScoreConfig scoreConfig = project.scoreConfig();
        if (scoreConfig == null) {
            throw new ScoreEntryConfigurationException(
                    "Challenge project score configuration is missing");
        }
        UUID studentMembershipId = memberships.findActiveStudentMembershipId(
                        studentId, activity.schoolId())
                .orElseThrow(ScoreEntryNotFoundException::new);
        var participant = activityParticipants.findByActivityAndMemberships(
                        activity.id().value(), List.of(studentMembershipId))
                .orElseThrow(() -> new ScoreEntryConflictException(
                        "Student is no longer in the activity roster"));
        if (!projectParticipants.existsByProjectAndParticipant(
                activityProjectId, participant.participantId())) {
            throw new ScoreEntryConflictException(
                    "Student is no longer assigned to this activity project");
        }
        return new EntryContext(
                activity.schoolId(), participant.participantId(), scoreConfig);
    }

    private static void requireActor(UUID actorId) {
        if (actorId == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
    }

    private static void requireCreateCommand(CreateTeacherScoreCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (command.activityProjectId() == null) {
            throw new IllegalArgumentException("activityProjectId is required");
        }
        if (command.studentId() == null) {
            throw new IllegalArgumentException("studentId is required");
        }
    }

    private static ScoreEntryConflictException noReviewer() {
        return new ScoreEntryConflictException(
                "NO_ELIGIBLE_SCORE_REVIEWER",
                "No other active school administrator can review this score");
    }

    private record EntryContext(
            UUID schoolId,
            UUID activityParticipantId,
            ScoreConfig scoreConfig) {
    }
}
