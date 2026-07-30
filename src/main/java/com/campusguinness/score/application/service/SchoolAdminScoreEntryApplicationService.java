package com.campusguinness.score.application.service;

import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.activity.internal.domain.Activity;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.activity.internal.domain.ExecutionStatus;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.project.application.port.ChallengeProjectRepository;
import com.campusguinness.project.internal.domain.ChallengeProject;
import com.campusguinness.project.internal.domain.ChallengeProjectId;
import com.campusguinness.project.internal.domain.ScoreConfig;
import com.campusguinness.score.application.command.CreateSchoolAdminScoreDraftCommand;
import com.campusguinness.score.application.command.UpdateSchoolAdminScoreDraftCommand;
import com.campusguinness.score.application.exception.ScoreEntryConfigurationException;
import com.campusguinness.score.application.exception.ScoreEntryConflictException;
import com.campusguinness.score.application.exception.ScoreEntryNotFoundException;
import com.campusguinness.score.application.port.ScoreAttemptNumberAllocatorPort;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.internal.domain.AttemptStatus;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SchoolAdminScoreEntryApplicationService {
    private final ScoreAttemptRepository attempts;
    private final ActivityProjectPort activityProjects;
    private final ActivityRepository activities;
    private final ChallengeProjectRepository projects;
    private final SchoolMembershipQueryPort memberships;
    private final ActivityParticipantQueryPort activityParticipants;
    private final ActivityProjectParticipantPort projectParticipants;
    private final ScoreAttemptNumberAllocatorPort attemptNumbers;

    public SchoolAdminScoreEntryApplicationService(
            ScoreAttemptRepository attempts,
            ActivityProjectPort activityProjects,
            ActivityRepository activities,
            ChallengeProjectRepository projects,
            SchoolMembershipQueryPort memberships,
            ActivityParticipantQueryPort activityParticipants,
            ActivityProjectParticipantPort projectParticipants,
            ScoreAttemptNumberAllocatorPort attemptNumbers) {
        this.attempts = attempts;
        this.activityProjects = activityProjects;
        this.activities = activities;
        this.projects = projects;
        this.memberships = memberships;
        this.activityParticipants = activityParticipants;
        this.projectParticipants = projectParticipants;
        this.attemptNumbers = attemptNumbers;
    }

    public UUID createDraft(UUID actorId, CreateSchoolAdminScoreDraftCommand command) {
        requireCreateCommand(command);
        UUID actorSchoolId = requireAdminSchool(actorId);
        EntryContext context = loadEntryContext(
                actorSchoolId, command.activityProjectId(), command.studentId());
        int attemptNumber = attemptNumbers.allocateNext(
                command.activityProjectId(), context.activityParticipantId(), command.studentId());
        ScoreValue value = buildScoreValue(
                context.scoreConfig(), command.integerValue(), command.decimalValue(),
                command.durationMs(), command.grade());
        EntryFields fields = validateEntryFields(
                value, command.scoreBusinessTime(), command.timeSource(), context.scoreConfig());

        ScoreAttempt attempt = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID()))
                .schoolId(actorSchoolId)
                .activityProjectId(command.activityProjectId())
                .studentId(command.studentId())
                .attemptNumber(attemptNumber)
                .scoreStorageType(scoreStorageType(context.scoreConfig()))
                .scoreValue(fields.value())
                .scoreBusinessTime(fields.businessTime())
                .timeSource(fields.timeSource())
                .enteredBy(actorId)
                .replacesId(null)
                .manualMakeup(false));
        attempts.save(attempt);
        return attempt.id().value();
    }

    public UUID updateDraft(
            UUID actorId,
            UUID attemptId,
            UpdateSchoolAdminScoreDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("request body is required");
        }
        UUID actorSchoolId = requireAdminSchool(actorId);
        ScoreAttempt attempt = lockOwnedAttempt(actorId, actorSchoolId, attemptId);
        if (attempt.status() != AttemptStatus.DRAFT
                && attempt.status() != AttemptStatus.REJECTED) {
            throw new ScoreEntryConflictException("Only DRAFT or REJECTED scores can be edited");
        }
        EntryContext context = loadEntryContext(
                actorSchoolId, attempt.activityProjectId(), attempt.studentId());
        ensureStoredTypeMatchesProject(attempt, context.scoreConfig());
        ScoreValue value = buildScoreValue(
                context.scoreConfig(), command.integerValue(), command.decimalValue(),
                command.durationMs(), command.grade());
        EntryFields fields = validateEntryFields(
                value, command.scoreBusinessTime(), command.timeSource(), context.scoreConfig());

        if (attempt.status() == AttemptStatus.REJECTED) {
            attempt.returnToDraft();
        }
        attempt.updateDraft(fields.value(), fields.businessTime(), fields.timeSource());
        attempts.save(attempt);
        return attempt.id().value();
    }

    public UUID submitDraft(UUID actorId, UUID attemptId) {
        UUID actorSchoolId = requireAdminSchool(actorId);
        ScoreAttempt attempt = lockOwnedAttempt(actorId, actorSchoolId, attemptId);
        if (attempt.status() != AttemptStatus.DRAFT) {
            throw new ScoreEntryConflictException("Only DRAFT scores can be submitted");
        }
        EntryContext context = loadEntryContext(
                actorSchoolId, attempt.activityProjectId(), attempt.studentId());
        ensureStoredTypeMatchesProject(attempt, context.scoreConfig());
        validateEntryFields(
                attempt.scoreValue(), attempt.scoreBusinessTime(),
                attempt.timeSource(), context.scoreConfig());
        if (!memberships.existsOtherActiveSchoolAdmin(actorSchoolId, actorId)) {
            throw new ScoreEntryConflictException(
                    "NO_ELIGIBLE_SCORE_REVIEWER",
                    "No other active school administrator can review this score");
        }
        attempt.submit();
        attempts.save(attempt);
        return attempt.id().value();
    }

    private UUID requireAdminSchool(UUID actorId) {
        if (actorId == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        return memberships.findActiveSchoolAdminSchoolId(actorId)
                .orElseThrow(() -> new AccessDeniedException(
                        "No active SCHOOL_ADMIN membership"));
    }

    private ScoreAttempt lockOwnedAttempt(
            UUID actorId, UUID actorSchoolId, UUID attemptId) {
        if (attemptId == null) {
            throw new ScoreEntryNotFoundException();
        }
        ScoreAttempt attempt = attempts.findByIdForUpdate(new ScoreAttemptId(attemptId))
                .orElseThrow(ScoreEntryNotFoundException::new);
        if (!attempt.schoolId().equals(actorSchoolId)) {
            throw new ScoreEntryNotFoundException();
        }
        if (!attempt.enteredBy().equals(actorId)) {
            throw new AccessDeniedException("Only the entrant can modify or submit this score");
        }
        return attempt;
    }

    private EntryContext loadEntryContext(
            UUID actorSchoolId, UUID activityProjectId, UUID studentId) {
        var activityProject = activityProjects.findById(activityProjectId)
                .orElseThrow(ScoreEntryNotFoundException::new);
        Activity activity = activities.findById(new ActivityId(activityProject.activityId()))
                .orElseThrow(ScoreEntryNotFoundException::new);
        if (!activity.schoolId().equals(actorSchoolId)) {
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
                        studentId, actorSchoolId)
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
                activityProjectId, participant.participantId(), scoreConfig);
    }

    private static ScoreValue buildScoreValue(
            ScoreConfig config,
            Long integerValue,
            BigDecimal decimalValue,
            Long durationMs,
            String grade) {
        var populated = List.of(
                integerValue != null,
                decimalValue != null,
                durationMs != null,
                grade != null && !grade.isBlank());
        if (populated.stream().filter(Boolean::booleanValue).count() != 1) {
            throw new IllegalArgumentException(
                    "Exactly one score value field must be provided");
        }
        return switch (config.storageType()) {
            case INTEGER -> {
                requireOtherValuesEmpty(decimalValue, durationMs, grade);
                if (integerValue == null || integerValue < 0) {
                    throw new IllegalArgumentException(
                            "integerValue must be greater than or equal to zero");
                }
                yield new ScoreValue.IntegerScore(integerValue);
            }
            case DECIMAL -> {
                requireOtherValuesEmpty(integerValue, durationMs, grade);
                if (decimalValue == null) {
                    throw new IllegalArgumentException("decimalValue is required");
                }
                validateDecimalScale(decimalValue, config.decimalPlaces());
                yield new ScoreValue.DecimalScore(decimalValue);
            }
            case DURATION -> {
                requireOtherValuesEmpty(integerValue, decimalValue, grade);
                if (durationMs == null || durationMs < 0) {
                    throw new IllegalArgumentException(
                            "durationMs must be greater than or equal to zero");
                }
                yield new ScoreValue.DurationScore(durationMs);
            }
            case GRADE -> {
                requireOtherValuesEmpty(integerValue, decimalValue, durationMs);
                String normalizedGrade = normalizeRequired(grade, 32, "grade");
                validateGrade(normalizedGrade, config.gradeOrder());
                yield new ScoreValue.GradeScore(normalizedGrade);
            }
        };
    }

    private static EntryFields validateEntryFields(
            ScoreValue value,
            Instant businessTime,
            String timeSource,
            ScoreConfig config) {
        if (businessTime == null) {
            throw new IllegalArgumentException("scoreBusinessTime is required");
        }
        String normalizedTimeSource = normalizeRequired(timeSource, 32, "timeSource");
        switch (value) {
            case ScoreValue.DecimalScore decimal ->
                    validateDecimalScale(decimal.value(), config.decimalPlaces());
            case ScoreValue.GradeScore grade ->
                    validateGrade(grade.grade(), config.gradeOrder());
            case ScoreValue.IntegerScore integer -> {
                if (integer.value() < 0) {
                    throw new IllegalArgumentException(
                            "integerValue must be greater than or equal to zero");
                }
            }
            case ScoreValue.DurationScore duration -> {
                if (duration.durationMs() < 0) {
                    throw new IllegalArgumentException(
                            "durationMs must be greater than or equal to zero");
                }
            }
        }
        return new EntryFields(value, businessTime, normalizedTimeSource);
    }

    private static void ensureStoredTypeMatchesProject(
            ScoreAttempt attempt, ScoreConfig config) {
        if (!attempt.scoreStorageType().name().equals(config.storageType().name())) {
            throw new ScoreEntryConfigurationException(
                    "Stored score type conflicts with project configuration");
        }
    }

    private static ScoreStorageType scoreStorageType(ScoreConfig config) {
        try {
            return ScoreStorageType.valueOf(config.storageType().name());
        } catch (IllegalArgumentException ex) {
            throw new ScoreEntryConfigurationException(
                    "Unsupported score storage type");
        }
    }

    private static void validateDecimalScale(BigDecimal value, Integer decimalPlaces) {
        if (decimalPlaces != null && value.scale() > decimalPlaces) {
            throw new IllegalArgumentException(
                    "decimalValue scale must not exceed decimalPlaces");
        }
    }

    private static void validateGrade(String grade, String gradeOrder) {
        if (gradeOrder == null || gradeOrder.isBlank()) {
            return;
        }
        List<String> order = Arrays.stream(gradeOrder.split(",", -1))
                .map(String::trim)
                .toList();
        if (order.stream().anyMatch(String::isEmpty)
                || new LinkedHashSet<>(order).size() != order.size()) {
            throw new ScoreEntryConfigurationException("gradeOrder is invalid");
        }
        if (!order.contains(grade)) {
            throw new IllegalArgumentException("grade must be present in gradeOrder");
        }
    }

    private static String normalizeRequired(String value, int maxLength, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static void requireCreateCommand(
            CreateSchoolAdminScoreDraftCommand command) {
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

    private static void requireOtherValuesEmpty(
            Object first, Object second, Object third) {
        boolean gradePresent = third instanceof String text && !text.isBlank();
        if (first != null || second != null || gradePresent
                || third != null && !(third instanceof String)) {
            throw new IllegalArgumentException(
                    "Score value fields must match the project score type");
        }
    }

    private record EntryContext(
            UUID activityProjectId,
            UUID activityParticipantId,
            ScoreConfig scoreConfig) {
    }

    private record EntryFields(
            ScoreValue value,
            Instant businessTime,
            String timeSource) {
    }
}
