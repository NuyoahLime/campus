package com.campusguinness.score.application.service;

import com.campusguinness.project.internal.domain.ScoreConfig;
import com.campusguinness.score.application.exception.ScoreEntryConfigurationException;
import com.campusguinness.score.internal.domain.ScoreAttempt;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Shared score-entry validation for school administrators and responsible teachers.
 */
public final class ScoreEntryValueFactory {
    private ScoreEntryValueFactory() {
    }

    public static EntryFields create(
            ScoreConfig config,
            Long integerValue,
            BigDecimal decimalValue,
            Long durationMs,
            String grade,
            Instant businessTime,
            String timeSource) {
        ScoreValue value = buildValue(
                config, integerValue, decimalValue, durationMs, grade);
        return validate(value, businessTime, timeSource, config);
    }

    public static EntryFields validate(
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

    public static void ensureStoredTypeMatchesProject(
            ScoreAttempt attempt, ScoreConfig config) {
        if (!attempt.scoreStorageType().name().equals(config.storageType().name())) {
            throw new ScoreEntryConfigurationException(
                    "Stored score type conflicts with project configuration");
        }
    }

    public static ScoreStorageType storageType(ScoreConfig config) {
        try {
            return ScoreStorageType.valueOf(config.storageType().name());
        } catch (IllegalArgumentException ex) {
            throw new ScoreEntryConfigurationException(
                    "Unsupported score storage type");
        }
    }

    private static ScoreValue buildValue(
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

    private static void requireOtherValuesEmpty(
            Object first, Object second, Object third) {
        boolean gradePresent = third instanceof String text && !text.isBlank();
        if (first != null || second != null || gradePresent
                || third != null && !(third instanceof String)) {
            throw new IllegalArgumentException(
                    "Score value fields must match the project score type");
        }
    }

    public record EntryFields(
            ScoreValue value,
            Instant businessTime,
            String timeSource) {
    }
}
