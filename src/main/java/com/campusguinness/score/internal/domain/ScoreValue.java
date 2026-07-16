package com.campusguinness.score.internal.domain;

import java.math.BigDecimal;

/**
 * Sealed interface for mutually exclusive score value types.
 * Exactly one variant must be used, matching the {@link ScoreStorageType} discriminator.
 */
public sealed interface ScoreValue {

    /** Integer score: whole-number value stored as long. */
    record IntegerScore(long value) implements ScoreValue {
        public IntegerScore {
            if (value < 0) throw new IllegalArgumentException("integer score must be >= 0");
        }
    }

    /** Decimal score: arbitrary precision value. */
    record DecimalScore(BigDecimal value) implements ScoreValue {
        public DecimalScore {
            if (value == null) throw new IllegalArgumentException("decimal score must not be null");
        }
    }

    /** Duration score: milliseconds, non-negative. */
    record DurationScore(long durationMs) implements ScoreValue {
        public DurationScore {
            if (durationMs < 0) throw new IllegalArgumentException("duration must be >= 0");
        }
    }

    /** Grade score: grade level code string. */
    record GradeScore(String grade) implements ScoreValue {
        public GradeScore {
            if (grade == null || grade.isBlank()) throw new IllegalArgumentException("grade must not be blank");
        }
    }
}
