package com.campusguinness.score.application.service;

import com.campusguinness.identity.application.exception.IdentityApplicationException;
import com.campusguinness.identity.application.service.SchoolResourceAuthorization;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.application.exception.ScoreWriteException;
import com.campusguinness.score.application.port.ActivityProjectLockPort;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.port.ScoreReviewRecordPort;
import com.campusguinness.score.application.port.ScoreCorrectionRecordPort;
import com.campusguinness.score.internal.domain.AttemptStatus;
import com.campusguinness.score.internal.domain.InvalidScoreAttemptStateTransitionException;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
import com.campusguinness.score.internal.domain.ScoreValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owns every application-level mutation of ScoreAttempt.currentEffective. */
@Service
@Transactional
public class EffectiveScoreApplicationService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScoreAttemptRepository attempts;
    private final ActivityProjectLockPort projects;
    private final ScoreReviewRecordPort reviews;
    private final ScoreCorrectionRecordPort corrections;
    private final SchoolResourceAuthorization authorization;
    private final CurrentActor actor;

    public EffectiveScoreApplicationService(ScoreAttemptRepository attempts,
                                            ActivityProjectLockPort projects,
                                            ScoreReviewRecordPort reviews,
                                            ScoreCorrectionRecordPort corrections,
                                            SchoolResourceAuthorization authorization,
                                            CurrentActor actor) {
        this.attempts = attempts;
        this.projects = projects;
        this.reviews = reviews;
        this.corrections = corrections;
        this.authorization = authorization;
        this.actor = actor;
    }

    public ScoreAttempt approve(UUID scoreAttemptId) {
        UUID reviewerId = actor.requireUserId();
        UUID schoolId = requireSchool();
        ScoreAttempt target = loadTarget(scoreAttemptId);
        ActivityProjectLockPort.Scope scope = lock(target.activityProjectId());
        target = reloadTarget(target.id().value());
        requireSameSchool(target, schoolId);
        try {
            target.approveForReview();
        } catch (InvalidScoreAttemptStateTransitionException ex) {
            throw conflict("SCORE_INVALID_STATE_TRANSITION", "Score attempt cannot approve from status " + target.status() + ".");
        }
        attempts.save(target);
        reviews.append(target.id().value(), reviewerId, "APPROVED", null);
        if (isAutomatic(scope)) {
            recalculate(scope, target.studentId(), target.activityProjectId());
        }
        return reloadTarget(target.id().value());
    }

    public ScoreAttempt designate(UUID scoreAttemptId, UUID expectedCurrentEffectiveAttemptId) {
        actor.requireUserId();
        UUID schoolId = requireSchool();
        ScoreAttempt target = loadTarget(scoreAttemptId);
        ActivityProjectLockPort.Scope scope = lock(target.activityProjectId());
        target = reloadTarget(target.id().value());
        requireSameSchool(target, schoolId);
        if (!"ADMIN_DESIGNATED".equals(scope.effectiveScoreRule())) {
            throw conflict("SCORE_RULE_INVALID", "Effective designation requires the ADMIN_DESIGNATED historical rule.");
        }
        List<ScoreAttempt> candidates = loadScope(target.studentId(), target.activityProjectId());
        ScoreAttempt current = current(candidates);
        UUID currentId = current == null ? null : current.id().value();
        if (!java.util.Objects.equals(currentId, expectedCurrentEffectiveAttemptId)) {
            throw conflict("SCORE_EFFECTIVE_CONFLICT", "The effective score changed before designation.");
        }
        if (target.status() != AttemptStatus.APPROVED) {
            throw conflict("SCORE_INVALID_STATE_TRANSITION", "Only approved attempts can be designated effective.");
        }
        switchEffective(candidates, target);
        return target;
    }

    public ScoreAttempt invalidate(UUID scoreAttemptId, UUID replacedById) {
        actor.requireUserId();
        UUID schoolId = requireSchool();
        ScoreAttempt target = loadTarget(scoreAttemptId);
        ActivityProjectLockPort.Scope scope = lock(target.activityProjectId());
        target = reloadTarget(target.id().value());
        requireSameSchool(target, schoolId);
        List<ScoreAttempt> candidates = loadScope(target.studentId(), target.activityProjectId());
        boolean wasCurrent = target.isCurrentEffective();
        try {
            target.invalidate(replacedById);
        } catch (InvalidScoreAttemptStateTransitionException ex) {
            throw conflict("SCORE_INVALID_STATE_TRANSITION", "Only approved attempts can be invalidated.");
        }
        attempts.save(target);
        if (wasCurrent && isAutomatic(scope)) {
            recalculate(scope, target.studentId(), target.activityProjectId());
        }
        return target;
    }

    /** Atomically allocates, approves, and makes a correction replacement effective. */
    public ScoreAttempt replaceForCorrection(ScoreAttempt oldAttempt, ScoreValue correctedValue,
                                             String reason, UUID correctedBy) {
        lock(oldAttempt.activityProjectId());
        ScoreAttempt authoritativeOld = reloadTarget(oldAttempt.id().value());
        if (authoritativeOld.status() != AttemptStatus.APPROVED) {
            throw conflict("SCORE_INVALID_STATE_TRANSITION", "Only approved attempts can be corrected.");
        }
        int nextAttemptNumber = loadScope(authoritativeOld.studentId(), authoritativeOld.activityProjectId()).stream()
                .mapToInt(ScoreAttempt::attemptNumber)
                .max()
                .orElse(0) + 1;
        ScoreAttempt replacement = ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID()))
                .schoolId(authoritativeOld.schoolId())
                .activityProjectId(authoritativeOld.activityProjectId())
                .studentId(authoritativeOld.studentId())
                .attemptNumber(nextAttemptNumber)
                .scoreStorageType(authoritativeOld.scoreStorageType())
                .scoreValue(correctedValue)
                .scoreBusinessTime(authoritativeOld.scoreBusinessTime())
                .timeSource(authoritativeOld.timeSource())
                .replacesId(authoritativeOld.id().value())
                .enteredBy(correctedBy)
                .manualMakeup(true));
        try {
            replacement.submit();
            replacement.approveForReview();
        } catch (InvalidScoreAttemptStateTransitionException ex) {
            throw conflict("SCORE_INVALID_STATE_TRANSITION", "Correction replacement has an invalid state.");
        }
        authoritativeOld.invalidate(replacement.id().value());
        attempts.save(authoritativeOld);
        attempts.save(replacement);
        corrections.append(authoritativeOld.id().value(), replacement.id().value(), reason, correctedBy);
        switchEffective(loadScope(replacement.studentId(), replacement.activityProjectId()), replacement);
        return replacement;
    }

    private void recalculate(ActivityProjectLockPort.Scope scope, UUID studentId, UUID activityProjectId) {
        List<ScoreAttempt> candidates = loadScope(studentId, activityProjectId);
        ScoreAttempt selected = select(scope, candidates);
        switchEffective(candidates, selected);
    }

    private void switchEffective(List<ScoreAttempt> attemptsInScope, ScoreAttempt selected) {
        ScoreAttempt current = current(attemptsInScope);
        if (selected != null && current != null && current.id().equals(selected.id())) {
            return;
        }
        for (ScoreAttempt candidate : attemptsInScope) {
            if (candidate.isCurrentEffective()) {
                candidate.clearCurrentEffective();
                attempts.save(candidate);
            }
        }
        if (selected != null) {
            selected.markCurrentEffective();
            attempts.save(selected);
        }
    }

    private ScoreAttempt select(ActivityProjectLockPort.Scope scope, List<ScoreAttempt> all) {
        List<ScoreAttempt> approved = all.stream()
                .filter(a -> a.status() == AttemptStatus.APPROVED)
                .toList();
        if (approved.isEmpty()) return null;
        return switch (scope.effectiveScoreRule()) {
            case "BEST" -> best(scope, approved);
            case "LAST" -> approved.stream().max(Comparator.comparingInt(ScoreAttempt::attemptNumber)).orElseThrow();
            case "ADMIN_DESIGNATED" -> current(all);
            default -> throw conflict("SCORE_RULE_INVALID", "Historical effective score rule is invalid.");
        };
    }

    private ScoreAttempt best(ActivityProjectLockPort.Scope scope, List<ScoreAttempt> approved) {
        Comparator<ScoreAttempt> valueComparator = switch (scope.scoreStorageType()) {
            case "INTEGER", "DECIMAL", "DURATION" -> numericBestComparator(scope, approved);
            case "GRADE" -> gradeBestComparator(scope, approved);
            default -> throw conflict("SCORE_RULE_INVALID", "Historical score storage type is invalid.");
        };
        return approved.stream().max(valueComparator.thenComparingInt(ScoreAttempt::attemptNumber)).orElseThrow();
    }

    private Comparator<ScoreAttempt> numericBestComparator(ActivityProjectLockPort.Scope scope,
                                                            List<ScoreAttempt> approved) {
        if (!"HIGHER_BETTER".equals(scope.comparisonDirection())
                && !"LOWER_BETTER".equals(scope.comparisonDirection())) {
            throw conflict("SCORE_RULE_INVALID", "Numeric BEST requires a historical comparison direction.");
        }
        // Validate every approved candidate before stream comparison so a single candidate is not skipped.
        approved.forEach(attempt -> comparableValue(scope, attempt.scoreValue()));
        Comparator<ScoreAttempt> comparator = Comparator.comparing(a -> comparableValue(scope, a.scoreValue()));
        return "LOWER_BETTER".equals(scope.comparisonDirection()) ? comparator.reversed() : comparator;
    }

    private Comparator<ScoreAttempt> gradeBestComparator(ActivityProjectLockPort.Scope scope,
                                                          List<ScoreAttempt> approved) {
        if (!"GRADE_ORDER".equals(scope.comparisonDirection())) {
            throw conflict("SCORE_RULE_INVALID", "GRADE BEST requires a historical grade order.");
        }
        List<String> grades = parseGradeOrder(scope.gradeOrder());
        // Validate every approved candidate before stream comparison so a single candidate is not skipped.
        approved.forEach(attempt -> gradeRank(grades, attempt.scoreValue()));
        return Comparator.comparing(a -> BigDecimal.valueOf(gradeRank(grades, a.scoreValue())));
    }

    private BigDecimal comparableValue(ActivityProjectLockPort.Scope scope, ScoreValue value) {
        return switch (scope.scoreStorageType()) {
            case "INTEGER" -> value instanceof ScoreValue.IntegerScore v ? BigDecimal.valueOf(v.value()) : invalidRuleValue();
            case "DECIMAL" -> value instanceof ScoreValue.DecimalScore v ? v.value() : invalidRuleValue();
            case "DURATION" -> value instanceof ScoreValue.DurationScore v ? BigDecimal.valueOf(v.durationMs()) : invalidRuleValue();
            case "GRADE" -> BigDecimal.valueOf(gradeRank(scope.gradeOrder(), value));
            default -> invalidRuleValue();
        };
    }

    private Integer gradeRank(String gradeOrder, ScoreValue value) {
        List<String> grades = parseGradeOrder(gradeOrder);
        return gradeRank(grades, value);
    }

    private Integer gradeRank(List<String> grades, ScoreValue value) {
        if (!(value instanceof ScoreValue.GradeScore grade)) return invalidRuleValue();
        int rank = grades.indexOf(grade.grade());
        if (rank < 0) throw conflict("SCORE_RULE_INVALID", "Approved grade is absent from the historical grade order.");
        return grades.size() - rank;
    }

    private List<String> parseGradeOrder(String raw) {
        if (raw == null || raw.isBlank()) throw conflict("SCORE_RULE_INVALID", "Historical grade order is required.");
        String normalized = raw.trim();
        try {
            JsonNode node = JSON.readTree(normalized);
            if (node.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : node) {
                    if (!item.isTextual() || item.textValue().isBlank()) {
                        throw conflict("SCORE_RULE_INVALID", "Historical grade order contains an invalid grade.");
                    }
                    result.add(item.textValue().trim());
                }
                if (result.isEmpty()) {
                    throw conflict("SCORE_RULE_INVALID", "Historical grade order is required.");
                }
                return requireDistinctGrades(result);
            }
            throw conflict("SCORE_RULE_INVALID", "Historical grade order JSON must be an array.");
        } catch (ScoreWriteException ex) {
            throw ex;
        } catch (Exception ignored) {
            // Existing project forms also persist comma-, semicolon-, or newline-delimited orders.
        }
        if (normalized.startsWith("[") || normalized.startsWith("{") || normalized.startsWith("\"")) {
            throw conflict("SCORE_RULE_INVALID", "Historical grade order JSON is malformed.");
        }
        List<String> result = java.util.Arrays.stream(normalized.split("[,;\\r\\n]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (result.isEmpty()) throw conflict("SCORE_RULE_INVALID", "Historical grade order is required.");
        return requireDistinctGrades(result);
    }

    private List<String> requireDistinctGrades(List<String> grades) {
        Set<String> seen = new HashSet<>();
        for (String grade : grades) {
            if (!seen.add(grade)) {
                throw conflict("SCORE_RULE_INVALID", "Historical grade order contains duplicate grades.");
            }
        }
        return grades;
    }

    private boolean isAutomatic(ActivityProjectLockPort.Scope scope) {
        return "BEST".equals(scope.effectiveScoreRule()) || "LAST".equals(scope.effectiveScoreRule());
    }

    private ScoreAttempt current(List<ScoreAttempt> candidates) {
        List<ScoreAttempt> effective = candidates.stream().filter(ScoreAttempt::isCurrentEffective).toList();
        if (effective.size() > 1) {
            throw conflict("SCORE_EFFECTIVE_DATA_CORRUPTION", "More than one current effective score exists.");
        }
        return effective.isEmpty() ? null : effective.getFirst();
    }

    private List<ScoreAttempt> loadScope(UUID studentId, UUID activityProjectId) {
        return attempts.findByStudentAndActivityProject(studentId, activityProjectId);
    }

    private ScoreAttempt loadTarget(UUID id) {
        if (id == null) throw conflict("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found.");
        return attempts.findById(new ScoreAttemptId(id))
                .orElseThrow(() -> conflict("SCORE_ATTEMPT_NOT_FOUND", "Score attempt not found."));
    }

    private ScoreAttempt reloadTarget(UUID id) {
        return loadTarget(id);
    }

    private ActivityProjectLockPort.Scope lock(UUID activityProjectId) {
        ActivityProjectLockPort.Scope scope = projects.lock(activityProjectId)
                .orElseThrow(() -> conflict("SCORE_ACTIVITY_PROJECT_NOT_FOUND", "Activity project not found."));
        if (scope.ruleVersionId() == null || scope.effectiveScoreRule() == null
                || scope.scoreStorageType() == null || scope.comparisonDirection() == null) {
            throw conflict("SCORE_RULE_INVALID", "Historical activity-project rule version is unavailable.");
        }
        return scope;
    }

    private UUID requireSchool() {
        try {
            return authorization.requireUniqueSchoolAdminSchool();
        } catch (IdentityApplicationException ex) {
            throw conflict("SCORE_SCOPE_DENIED", "Score management scope denied.");
        }
    }

    private void requireSameSchool(ScoreAttempt score, UUID schoolId) {
        if (!schoolId.equals(score.schoolId())) {
            throw conflict("SCORE_SCOPE_DENIED", "Score management scope denied.");
        }
    }

    private ScoreWriteException conflict(String code, String message) {
        return new ScoreWriteException(code, message);
    }

    private <T> T invalidRuleValue() {
        throw conflict("SCORE_RULE_INVALID", "Score value does not match the historical rule.");
    }
}
