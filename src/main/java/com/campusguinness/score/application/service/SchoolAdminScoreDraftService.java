package com.campusguinness.score.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.application.exception.ScoreWriteException;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.port.ScoreWriteContextPort;
import com.campusguinness.score.internal.domain.AttemptStatus;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class SchoolAdminScoreDraftService {
    private static final List<String> WRITABLE_ACTIVITY_STATES = List.of("PUBLISHED", "IN_PROGRESS", "ENDED");
    private static final List<String> SCORE_STATUSES = List.of(
            "DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED", "INVALIDATED");

    private final ScoreAttemptRepository attempts;
    private final ScoreWriteContextPort context;
    private final SchoolResourceAuthorization authorization;
    private final CurrentActor actor;
    private final ObjectMapper objectMapper;

    public SchoolAdminScoreDraftService(ScoreAttemptRepository attempts,
                                        ScoreWriteContextPort context,
                                        SchoolResourceAuthorization authorization,
                                        CurrentActor actor,
                                        ObjectMapper objectMapper) {
        this.attempts = attempts;
        this.context = context;
        this.authorization = authorization;
        this.actor = actor;
        this.objectMapper = objectMapper;
    }

    public ScoreAttempt createDraft(UUID activityProjectId, UUID studentId,
                                    Long integerValue, BigDecimal decimalValue,
                                    Long durationMs, String grade, Instant businessTime) {
        UUID enteredBy = actor.requireUserId();
        UUID schoolId = requireSchool();
        ScoreWriteContextPort.Context scoreContext = loadContext(activityProjectId);
        requireSameSchool(scoreContext.schoolId(), schoolId);
        requireWritableActivity(scoreContext.activityStatus());

        ScoreWriteContextPort.Student student = requireParticipant(scoreContext, studentId, schoolId);
        ScoreValue scoreValue = value(scoreContext, integerValue, decimalValue, durationMs, grade);
        ScoreAttempt score = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID()))
                .schoolId(schoolId)
                .activityProjectId(activityProjectId)
                .studentId(student.userId())
                .attemptNumber(context.nextAttemptNumber(activityProjectId, student.userId()))
                .scoreStorageType(ScoreStorageType.valueOf(scoreContext.scoreStorageType()))
                .scoreValue(scoreValue)
                .scoreBusinessTime(businessTime)
                .timeSource(null)
                .replacesId(null)
                .enteredBy(enteredBy));
        attempts.save(score);
        return score;
    }

    public ScoreAttempt updateDraft(UUID scoreAttemptId,
                                    Long integerValue, BigDecimal decimalValue,
                                    Long durationMs, String grade, Instant businessTime) {
        UUID schoolId = requireSchool();
        ScoreAttempt score = attempts.findById(new ScoreAttemptId(scoreAttemptId))
                .orElseThrow(() -> error("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found."));
        requireSameSchool(score.schoolId(), schoolId);
        if (score.status() != AttemptStatus.DRAFT) {
            throw error("SCORE_INVALID_STATE_TRANSITION", "Only draft scores can be edited.");
        }

        ScoreWriteContextPort.Context scoreContext = loadContext(score.activityProjectId());
        requireSameSchool(scoreContext.schoolId(), schoolId);
        requireWritableActivity(scoreContext.activityStatus());
        requireParticipant(scoreContext, score.studentId(), schoolId);

        score.updateScoreValue(value(scoreContext, integerValue, decimalValue, durationMs, grade));
        score.updateScoreBusinessTime(businessTime);
        attempts.save(score);
        return score;
    }

    @Transactional(readOnly = true)
    public ActivityScores activityScores(UUID activityId, UUID activityProjectId, String status) {
        UUID schoolId = requireSchool();
        ScoreWriteContextPort.Activity activity = requireActivity(activityId, schoolId);
        String normalizedStatus = normalizeStatus(status);
        if (activityProjectId != null) {
            ScoreWriteContextPort.Context project = loadContext(activityProjectId);
            if (!project.activityId().equals(activityId)) {
                throw error("SCORE_ACTIVITY_PROJECT_NOT_FOUND", "Activity project not found.");
            }
            requireSameSchool(project.schoolId(), schoolId);
        }
        return new ActivityScores(activity.activityId(), activity.title(), activity.activityStatus(),
                context.findScores(activityId, schoolId, activityProjectId, normalizedStatus));
    }

    @Transactional(readOnly = true)
    public ActivityCandidates scoreCandidates(UUID activityId) {
        UUID schoolId = requireSchool();
        ScoreWriteContextPort.Activity activity = requireActivity(activityId, schoolId);
        LinkedHashMap<UUID, CandidateBuilder> candidates = new LinkedHashMap<>();
        for (ScoreWriteContextPort.CandidateRow row : context.findCandidates(activityId, schoolId)) {
            CandidateBuilder candidate = candidates.computeIfAbsent(row.studentId(), ignored ->
                    new CandidateBuilder(row.studentId(), row.displayName(), row.studentNumber()));
            candidate.projects.add(new CandidateProject(row.activityProjectId(), row.projectName(),
                    row.scoreStorageType(), row.latestAttemptId(), row.latestAttemptNumber(), row.latestStatus()));
        }
        return new ActivityCandidates(activity.activityId(), activity.title(), activity.activityStatus(),
                candidates.values().stream().map(CandidateBuilder::build).toList());
    }

    @Transactional(readOnly = true)
    public ScoreWriteContextPort.ScoreRow scoreDetail(UUID scoreAttemptId) {
        UUID schoolId = requireSchool();
        ScoreWriteContextPort.ScoreRow score = context.findScore(scoreAttemptId)
                .orElseThrow(() -> error("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found."));
        ScoreWriteContextPort.Activity activity = context.findActivity(score.activityId())
                .orElseThrow(() -> error("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found."));
        if (!schoolId.equals(activity.schoolId())) {
            throw error("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found.");
        }
        return score;
    }

    private UUID requireSchool() {
        try {
            return authorization.requireUniqueSchoolAdminSchool();
        } catch (IdentityApplicationException ex) {
            throw error("SCORE_SCOPE_DENIED", "Score management scope denied.");
        }
    }

    private ScoreWriteContextPort.Activity requireActivity(UUID activityId, UUID schoolId) {
        ScoreWriteContextPort.Activity activity = context.findActivity(activityId)
                .orElseThrow(() -> error("SCORE_ACTIVITY_NOT_FOUND", "Activity not found."));
        requireSameSchool(activity.schoolId(), schoolId);
        return activity;
    }

    private ScoreWriteContextPort.Context loadContext(UUID activityProjectId) {
        return context.findContext(activityProjectId)
                .orElseThrow(() -> error("SCORE_ACTIVITY_PROJECT_NOT_FOUND", "Activity project not found."));
    }

    private ScoreWriteContextPort.Student requireParticipant(ScoreWriteContextPort.Context scoreContext,
                                                             UUID studentId, UUID schoolId) {
        ScoreWriteContextPort.Student student = context.findActiveStudent(studentId, schoolId)
                .orElseThrow(() -> error("SCORE_STUDENT_NOT_FOUND", "Student not found."));
        if (!context.isParticipant(scoreContext.activityId(), student.membershipId())) {
            throw error("SCORE_STUDENT_NOT_PARTICIPANT", "Student is not an activity participant.");
        }
        return student;
    }

    private void requireSameSchool(UUID resourceSchoolId, UUID actorSchoolId) {
        if (!actorSchoolId.equals(resourceSchoolId)) {
            throw error("SCORE_SCOPE_DENIED", "Score management scope denied.");
        }
    }

    private void requireWritableActivity(String status) {
        if (!WRITABLE_ACTIVITY_STATES.contains(status)) {
            throw error("SCORE_INVALID_ACTIVITY_STATE", "Activity is not writable for score drafts.");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!SCORE_STATUSES.contains(normalized)) {
            throw error("SCORE_INVALID_VALUE", "Score status filter is invalid.");
        }
        return normalized;
    }

    private ScoreValue value(ScoreWriteContextPort.Context scoreContext,
                             Long integerValue, BigDecimal decimalValue,
                             Long durationMs, String grade) {
        try {
            return switch (ScoreStorageType.valueOf(scoreContext.scoreStorageType())) {
                case INTEGER -> integerValue != null && decimalValue == null && durationMs == null && grade == null
                        ? new ScoreValue.IntegerScore(integerValue) : invalidValue();
                case DECIMAL -> decimalValue != null && integerValue == null && durationMs == null && grade == null
                        ? decimalValue(scoreContext, decimalValue) : invalidValue();
                case DURATION -> durationMs != null && integerValue == null && decimalValue == null && grade == null
                        ? new ScoreValue.DurationScore(durationMs) : invalidValue();
                case GRADE -> grade != null && !grade.isBlank() && integerValue == null
                        && decimalValue == null && durationMs == null
                        ? gradeValue(scoreContext, grade.trim()) : invalidValue();
            };
        } catch (ScoreWriteException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw error("SCORE_INVALID_VALUE", "Score value is invalid.");
        }
    }

    private ScoreValue decimalValue(ScoreWriteContextPort.Context scoreContext, BigDecimal value) {
        int configuredPlaces = scoreContext.decimalPlaces() == null ? 4 : scoreContext.decimalPlaces();
        int actualPlaces = Math.max(0, value.stripTrailingZeros().scale());
        if (configuredPlaces < 0 || actualPlaces > configuredPlaces
                || value.scale() > 4 || value.precision() > 18) {
            throw error("SCORE_INVALID_VALUE", "Decimal score does not match the historical rule precision.");
        }
        return new ScoreValue.DecimalScore(value);
    }

    private ScoreValue gradeValue(ScoreWriteContextPort.Context scoreContext, String grade) {
        if (!gradeValues(scoreContext.gradeOrder()).contains(grade)) {
            throw error("SCORE_INVALID_VALUE", "Grade is not present in the historical rule order.");
        }
        return new ScoreValue.GradeScore(grade);
    }

    private List<String> gradeValues(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                node.forEach(item -> {
                    if (item.isTextual() && !item.textValue().isBlank()) values.add(item.textValue().trim());
                });
                return values;
            }
        } catch (Exception ignored) {
            // Existing project forms historically accepted delimited text as well as JSON arrays.
        }
        return List.of(raw.split("[,;\\r\\n]+"))
                .stream().map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private ScoreValue invalidValue() {
        throw error("SCORE_INVALID_VALUE", "Exactly one score value matching the historical rule is required.");
    }

    private ScoreWriteException error(String code, String message) {
        return new ScoreWriteException(code, message);
    }

    public record ActivityScores(UUID activityId, String activityTitle, String activityStatus,
                                 List<ScoreWriteContextPort.ScoreRow> scores) {}
    public record ActivityCandidates(UUID activityId, String activityTitle, String activityStatus,
                                     List<Candidate> candidates) {}
    public record Candidate(UUID studentId, String studentDisplay, String studentNumber,
                            List<CandidateProject> projects) {}
    public record CandidateProject(UUID activityProjectId, String projectName, String scoreStorageType,
                                   UUID latestAttemptId, Integer latestAttemptNumber, String latestStatus) {}

    private static final class CandidateBuilder {
        private final UUID studentId;
        private final String displayName;
        private final String studentNumber;
        private final List<CandidateProject> projects = new ArrayList<>();

        private CandidateBuilder(UUID studentId, String displayName, String studentNumber) {
            this.studentId = studentId;
            this.displayName = displayName;
            this.studentNumber = studentNumber;
        }

        private Candidate build() {
            return new Candidate(studentId, displayName, studentNumber, List.copyOf(projects));
        }
    }
}
