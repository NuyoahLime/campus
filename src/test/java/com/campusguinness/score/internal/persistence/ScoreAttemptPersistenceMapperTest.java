package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.internal.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ScoreAttemptPersistenceMapper")
class ScoreAttemptPersistenceMapperTest {

    @Nested @DisplayName("Entity → Domain (all ScoreValue types)")
    class ToDomain {
        @Test void restoresIntegerScore() {
            var e = entity("INTEGER"); e.setScoreValue(java.math.BigDecimal.valueOf(42));
            var s = ScoreAttemptPersistenceMapper.toDomain(e);
            assertThat(s.scoreValue()).isInstanceOf(ScoreValue.IntegerScore.class);
            assertThat(((ScoreValue.IntegerScore)s.scoreValue()).value()).isEqualTo(42);
            assertThat(s.domainEvents()).isEmpty();
        }
        @Test void restoresDecimalScore() {
            var e = entity("DECIMAL"); e.setScoreValue(new java.math.BigDecimal("98.76"));
            var s = ScoreAttemptPersistenceMapper.toDomain(e);
            assertThat(s.scoreValue()).isInstanceOf(ScoreValue.DecimalScore.class);
            assertThat(s.domainEvents()).isEmpty();
        }
        @Test void restoresDurationScore() {
            var e = entity("DURATION"); e.setScoreDurationMs(12500L);
            var s = ScoreAttemptPersistenceMapper.toDomain(e);
            assertThat(s.scoreValue()).isInstanceOf(ScoreValue.DurationScore.class);
            assertThat(s.domainEvents()).isEmpty();
        }
        @Test void restoresGradeScore() {
            var e = entity("GRADE"); e.setScoreGrade("优秀");
            var s = ScoreAttemptPersistenceMapper.toDomain(e);
            assertThat(s.scoreValue()).isInstanceOf(ScoreValue.GradeScore.class);
            assertThat(s.domainEvents()).isEmpty();
        }
        @Test void restoresApprovedStatus() {
            var e = entity("INTEGER"); e.setScoreValue(java.math.BigDecimal.valueOf(100));
            e.setScoreStatus("APPROVED"); e.setCurrentEffective(true);
            var s = ScoreAttemptPersistenceMapper.toDomain(e);
            assertThat(s.status()).isEqualTo(AttemptStatus.APPROVED);
            assertThat(s.isCurrentEffective()).isTrue();
            assertThat(s.domainEvents()).isEmpty();
        }
    }
    @Nested @DisplayName("Domain → Entity")
    class ToEntity {
        @Test void mapsIntegerScore() {
            var s = build(new ScoreValue.IntegerScore(42), ScoreStorageType.INTEGER);
            var e = ScoreAttemptPersistenceMapper.toEntity(s);
            assertThat(e.getScoreStorageType()).isEqualTo("INTEGER");
            assertThat(e.getScoreValue()).isEqualTo(java.math.BigDecimal.valueOf(42));
        }
    }

    @Nested @DisplayName("Round-trip: Domain → Entity → Domain")
    class RoundTrip {
        @Test void integerScorePreservesValue() {
            var orig = build(new ScoreValue.IntegerScore(99999), ScoreStorageType.INTEGER);
            var restored = ScoreAttemptPersistenceMapper.toDomain(ScoreAttemptPersistenceMapper.toEntity(orig));
            assertThat(restored.scoreValue()).isEqualTo(orig.scoreValue());
        }
        @Test void decimalScorePreservesPrecision() {
            var orig = build(new ScoreValue.DecimalScore(new java.math.BigDecimal("123.4567")), ScoreStorageType.DECIMAL);
            var restored = ScoreAttemptPersistenceMapper.toDomain(ScoreAttemptPersistenceMapper.toEntity(orig));
            assertThat(((ScoreValue.DecimalScore)restored.scoreValue()).value().compareTo(new java.math.BigDecimal("123.4567"))).isEqualTo(0);
        }
        @Test void durationScorePreservesMs() {
            var orig = build(new ScoreValue.DurationScore(9876543210L), ScoreStorageType.DURATION);
            var restored = ScoreAttemptPersistenceMapper.toDomain(ScoreAttemptPersistenceMapper.toEntity(orig));
            assertThat(((ScoreValue.DurationScore)restored.scoreValue()).durationMs()).isEqualTo(9876543210L);
        }
        @Test void gradeScorePreservesString() {
            var orig = build(new ScoreValue.GradeScore("优秀"), ScoreStorageType.GRADE);
            var restored = ScoreAttemptPersistenceMapper.toDomain(ScoreAttemptPersistenceMapper.toEntity(orig));
            assertThat(((ScoreValue.GradeScore)restored.scoreValue()).grade()).isEqualTo("优秀");
        }
    }

    @Nested @DisplayName("Storage field exclusivity")
    class FieldExclusivity {
        @Test void durationKeepsOtherFieldsNull() {
            var s = build(new ScoreValue.DurationScore(5000L), ScoreStorageType.DURATION);
            var e = ScoreAttemptPersistenceMapper.toEntity(s);
            assertThat(e.getScoreValue()).isNull();
            assertThat(e.getScoreGrade()).isNull();
            assertThat(e.getScoreDurationMs()).isEqualTo(5000L);
        }
        @Test void gradeKeepsOtherFieldsNull() {
            var s = build(new ScoreValue.GradeScore("A"), ScoreStorageType.GRADE);
            var e = ScoreAttemptPersistenceMapper.toEntity(s);
            assertThat(e.getScoreValue()).isNull();
            assertThat(e.getScoreDurationMs()).isNull();
            assertThat(e.getScoreGrade()).isEqualTo("A");
        }
        @Test void decimalKeepsDurationAndGradeNull() {
            var s = build(new ScoreValue.DecimalScore(new java.math.BigDecimal("3.14")), ScoreStorageType.DECIMAL);
            var e = ScoreAttemptPersistenceMapper.toEntity(s);
            assertThat(e.getScoreDurationMs()).isNull();
            assertThat(e.getScoreGrade()).isNull();
        }
    }
    @Nested @DisplayName("Corrupted entity → exception")
    class Corruption {
        @Test void integerNullValueFails() {
            var e = entity("INTEGER"); e.setScoreValue(null);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("null");
        }
        @Test void integerFractionalFails() {
            var e = entity("INTEGER"); e.setScoreValue(new java.math.BigDecimal("1.5"));
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("not exact long");
        }
        @Test void integerPositiveOverflowFails() {
            var e = entity("INTEGER");
            e.setScoreValue(java.math.BigDecimal.valueOf(Long.MAX_VALUE).add(java.math.BigDecimal.ONE));
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class);
        }
        @Test void integerNegativeOverflowFails() {
            var e = entity("INTEGER");
            e.setScoreValue(java.math.BigDecimal.valueOf(Long.MIN_VALUE).subtract(java.math.BigDecimal.ONE));
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class);
        }
        @Test void integerBoundaryMaxRoundTrip() {
            var e = entity("INTEGER"); e.setScoreValue(java.math.BigDecimal.valueOf(Long.MAX_VALUE));
            var s = ScoreAttemptPersistenceMapper.toDomain(e);
            assertThat(((ScoreValue.IntegerScore)s.scoreValue()).value()).isEqualTo(Long.MAX_VALUE);
        }
        @Test void integerBoundaryMinRoundTrip() {
            var e = entity("INTEGER"); e.setScoreValue(java.math.BigDecimal.valueOf(0));
            var s = ScoreAttemptPersistenceMapper.toDomain(e);
            assertThat(((ScoreValue.IntegerScore)s.scoreValue()).value()).isEqualTo(0);
        }
        @Test void nullStorageTypeFails() {
            var e = entity("INTEGER"); e.setScoreValue(java.math.BigDecimal.ONE);
            e.setScoreStorageType(null);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class);
        }
        @Test void integerWithConflictingGradeFails() {
            var e = entity("INTEGER"); e.setScoreValue(java.math.BigDecimal.valueOf(1)); e.setScoreGrade("A");
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("conflicting");
        }
        @Test void integerWithConflictingDurationFails() {
            var e = entity("INTEGER"); e.setScoreValue(java.math.BigDecimal.valueOf(1)); e.setScoreDurationMs(100L);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("conflicting");
        }
        @Test void decimalNullValueFails() {
            var e = entity("DECIMAL"); e.setScoreValue(null);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class);
        }
        @Test void decimalWithConflictingDurationFails() {
            var e = entity("DECIMAL"); e.setScoreValue(java.math.BigDecimal.ONE); e.setScoreDurationMs(100L);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("conflicting");
        }
        @Test void durationNullValueFails() {
            var e = entity("DURATION"); e.setScoreDurationMs(null);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class);
        }
        @Test void durationWithConflictingValueFails() {
            var e = entity("DURATION"); e.setScoreDurationMs(1000L); e.setScoreValue(java.math.BigDecimal.ONE);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("conflicting");
        }
        @Test void durationWithConflictingGradeFails() {
            var e = entity("DURATION"); e.setScoreDurationMs(1000L); e.setScoreGrade("A");
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("conflicting");
        }
        @Test void gradeNullValueFails() {
            var e = entity("GRADE"); e.setScoreGrade(null);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class);
        }
        @Test void gradeWithConflictingValueFails() {
            var e = entity("GRADE"); e.setScoreGrade("A"); e.setScoreValue(java.math.BigDecimal.ONE);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("conflicting");
        }
        @Test void gradeWithConflictingDurationFails() {
            var e = entity("GRADE"); e.setScoreGrade("A"); e.setScoreDurationMs(100L);
            assertThatThrownBy(() -> ScoreAttemptPersistenceMapper.toDomain(e))
                    .isInstanceOf(ScoreValuePersistenceException.class).hasMessageContaining("conflicting");
        }
    }

    private ScoreAttemptEntity entity(String type) {
        var e = new ScoreAttemptEntity(); e.setId(UUID.randomUUID()); e.setSchoolId(UUID.randomUUID());
        e.setActivityProjectId(UUID.randomUUID()); e.setStudentId(UUID.randomUUID());
        e.setAttemptNumber(1); e.setScoreStorageType(type); e.setScoreStatus("DRAFT");
        e.setEnteredBy(UUID.randomUUID()); e.setCurrentEffective(false); e.setManualMakeup(false);
        return e;
    }
    private ScoreAttempt build(ScoreValue v, ScoreStorageType t) {
        return ScoreAttempt.create(new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(UUID.randomUUID())).schoolId(UUID.randomUUID())
                .activityProjectId(UUID.randomUUID()).studentId(UUID.randomUUID())
                .attemptNumber(1).scoreStorageType(t).scoreValue(v)
                .scoreBusinessTime(Instant.now()).enteredBy(UUID.randomUUID()));
    }
}
