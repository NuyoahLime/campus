package com.campusguinness.score.application.service;

import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.score.application.exception.ScoreConfigurationException;
import com.campusguinness.score.application.exception.ScoreReviewConflictException;
import com.campusguinness.score.application.exception.ScoreReviewNotFoundException;
import com.campusguinness.score.application.port.ScoreAttemptRepository;
import com.campusguinness.score.application.port.ScoreReviewContextPort;
import com.campusguinness.score.application.port.ScoreReviewRecordPort;
import com.campusguinness.score.internal.domain.AttemptStatus;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreAttemptId;
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
public class ScoreReviewApplicationService {
    private final ScoreAttemptRepository attempts;
    private final ScoreReviewContextPort contextPort;
    private final ScoreReviewRecordPort reviewRecords;
    private final SchoolMembershipQueryPort memberships;

    public ScoreReviewApplicationService(ScoreAttemptRepository attempts,
                                         ScoreReviewContextPort contextPort,
                                         ScoreReviewRecordPort reviewRecords,
                                         SchoolMembershipQueryPort memberships) {
        this.attempts = attempts;
        this.contextPort = contextPort;
        this.reviewRecords = reviewRecords;
        this.memberships = memberships;
    }

    public void approve(UUID attemptId, UUID reviewerId, String reviewComment,
                        Boolean makeCurrentEffective) {
        String normalizedComment = normalizeOptional(reviewComment, 2000, "reviewComment");
        UUID actorSchoolId = requireAdminSchool(reviewerId);
        ScoreAttempt target = lockAndValidate(attemptId, reviewerId, actorSchoolId);
        var context = requireContext(attemptId, actorSchoolId);

        if (!target.scoreStorageType().name().equals(context.scoreStorageType())) {
            throw new ScoreReviewConflictException("Score storage type conflicts with project configuration");
        }

        String rule = context.effectiveScoreRule();
        if ("ADMIN_DESIGNATED".equals(rule)) {
            if (makeCurrentEffective == null) {
                throw new IllegalArgumentException(
                        "makeCurrentEffective is required for ADMIN_DESIGNATED");
            }
        } else if (makeCurrentEffective != null) {
            throw new IllegalArgumentException(
                    "makeCurrentEffective is only allowed for ADMIN_DESIGNATED");
        }
        if (!List.of("BEST", "LAST", "ADMIN_DESIGNATED").contains(rule)) {
            throw new ScoreConfigurationException("Unsupported effective score rule");
        }

        ScoreAttempt current = attempts.findCurrentEffectiveForUpdate(
                target.activityProjectId(), target.studentId()).orElse(null);
        if (current != null && current.id().equals(target.id())) {
            throw new ScoreReviewConflictException("Pending score cannot already be current effective");
        }
        if (current != null && current.status() != AttemptStatus.APPROVED) {
            throw new ScoreReviewConflictException("Current effective score is not approved");
        }

        boolean promote = switch (rule) {
            case "LAST" -> true;
            case "ADMIN_DESIGNATED" -> Boolean.TRUE.equals(makeCurrentEffective);
            case "BEST" -> current == null || isStrictlyBetter(target, current, context);
            default -> throw new ScoreConfigurationException("Unsupported effective score rule");
        };

        if (promote && current != null) {
            current.changeCurrentEffective(false);
            attempts.save(current);
        }
        target.approve(promote);
        attempts.save(target);
        reviewRecords.append(new ScoreReviewRecordPort.ScoreReviewRecord(
                UUID.randomUUID(), target.id().value(), reviewerId, "APPROVED",
                normalizedComment, null, Instant.now()));
    }

    public void reject(UUID attemptId, UUID reviewerId, String rejectReason,
                       String reviewComment) {
        String normalizedReason = normalizeRequired(rejectReason, 1000, "rejectReason");
        String normalizedComment = normalizeOptional(reviewComment, 2000, "reviewComment");
        UUID actorSchoolId = requireAdminSchool(reviewerId);
        ScoreAttempt target = lockAndValidate(attemptId, reviewerId, actorSchoolId);
        requireContext(attemptId, actorSchoolId);

        target.reject(normalizedReason);
        attempts.save(target);
        reviewRecords.append(new ScoreReviewRecordPort.ScoreReviewRecord(
                UUID.randomUUID(), target.id().value(), reviewerId, "REJECTED",
                normalizedComment, normalizedReason, Instant.now()));
    }

    private UUID requireAdminSchool(UUID reviewerId) {
        return memberships.findActiveSchoolAdminSchoolId(reviewerId)
                .orElseThrow(() -> new AccessDeniedException(
                        "No active SCHOOL_ADMIN membership"));
    }

    private ScoreAttempt lockAndValidate(UUID attemptId, UUID reviewerId, UUID actorSchoolId) {
        ScoreAttempt target = attempts.findByIdForUpdate(new ScoreAttemptId(attemptId))
                .orElseThrow(ScoreReviewNotFoundException::new);
        if (!target.schoolId().equals(actorSchoolId)) {
            throw new ScoreReviewNotFoundException();
        }
        if (target.status() != AttemptStatus.PENDING_REVIEW) {
            throw new ScoreReviewConflictException("ScoreAttempt is no longer pending review");
        }
        if (target.enteredBy().equals(reviewerId)) {
            throw new AccessDeniedException("Cannot review a score entered by yourself");
        }
        return target;
    }

    private ScoreReviewContextPort.ReviewContext requireContext(UUID attemptId, UUID actorSchoolId) {
        return contextPort.findReviewContext(attemptId, actorSchoolId)
                .orElseThrow(ScoreReviewNotFoundException::new);
    }

    private boolean isStrictlyBetter(
            ScoreAttempt candidate,
            ScoreAttempt current,
            ScoreReviewContextPort.ReviewContext context) {
        if (context.comparisonDirection() == null) {
            throw new ScoreConfigurationException("Comparison direction is missing");
        }
        if ("NO_RANKING".equals(context.comparisonDirection())) {
            return false;
        }
        if (candidate.scoreStorageType() != current.scoreStorageType()) {
            throw new ScoreReviewConflictException("Effective scores have incompatible storage types");
        }

        int comparison;
        if (candidate.scoreValue() instanceof ScoreValue.GradeScore candidateGrade
                && current.scoreValue() instanceof ScoreValue.GradeScore currentGrade) {
            if (!"GRADE_ORDER".equals(context.comparisonDirection())) {
                throw new ScoreConfigurationException("GRADE scores require GRADE_ORDER");
            }
            List<String> order = parseGradeOrder(context.gradeOrder());
            int candidateIndex = order.indexOf(candidateGrade.grade());
            int currentIndex = order.indexOf(currentGrade.grade());
            if (candidateIndex < 0 || currentIndex < 0) {
                throw new ScoreConfigurationException("Score grade is missing from gradeOrder");
            }
            return candidateIndex < currentIndex;
        }

        comparison = numericValue(candidate.scoreValue()).compareTo(
                numericValue(current.scoreValue()));
        return switch (context.comparisonDirection()) {
            case "HIGHER_BETTER" -> comparison > 0;
            case "LOWER_BETTER" -> comparison < 0;
            case "GRADE_ORDER" -> throw new ScoreConfigurationException(
                    "GRADE_ORDER can only compare GRADE scores");
            default -> throw new ScoreConfigurationException("Unsupported comparison direction");
        };
    }

    private static BigDecimal numericValue(ScoreValue value) {
        return switch (value) {
            case ScoreValue.IntegerScore integer -> BigDecimal.valueOf(integer.value());
            case ScoreValue.DecimalScore decimal -> decimal.value();
            case ScoreValue.DurationScore duration -> BigDecimal.valueOf(duration.durationMs());
            case ScoreValue.GradeScore ignored -> throw new ScoreConfigurationException(
                    "GRADE score requires gradeOrder");
        };
    }

    private static List<String> parseGradeOrder(String value) {
        if (value == null || value.isBlank()) {
            throw new ScoreConfigurationException("gradeOrder is missing");
        }
        List<String> order = Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .toList();
        if (order.stream().anyMatch(String::isEmpty)
                || new LinkedHashSet<>(order).size() != order.size()) {
            throw new ScoreConfigurationException("gradeOrder is invalid");
        }
        return order;
    }

    private static String normalizeRequired(String value, int maxLength, String field) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
