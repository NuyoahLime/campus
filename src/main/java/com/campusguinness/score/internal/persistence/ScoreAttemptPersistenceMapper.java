package com.campusguinness.score.internal.persistence;

import com.campusguinness.score.internal.domain.*;
import java.time.Instant;

final class ScoreAttemptPersistenceMapper {
    private ScoreAttemptPersistenceMapper() {}

    static ScoreAttemptEntity toEntity(ScoreAttempt domain) {
        var e = new ScoreAttemptEntity();
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setActivityProjectId(domain.activityProjectId()); e.setStudentId(domain.studentId());
        e.setAttemptNumber(domain.attemptNumber());
        e.setScoreStorageType(domain.scoreStorageType().name());
        mapScoreValueToEntity(domain.scoreValue(), e);
        e.setScoreBusinessTime(domain.scoreBusinessTime()); e.setTimeSource(domain.timeSource());
        e.setCurrentEffective(domain.isCurrentEffective()); e.setReplacesId(domain.replacesId());
        e.setScoreStatus(domain.status().name()); e.setEnteredBy(domain.enteredBy());
        e.setSubmittedAt(domain.submittedAt()); e.setManualMakeup(domain.isManualMakeup());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    static void updateEntity(ScoreAttemptEntity e, ScoreAttempt domain) {
        e.setScoreValue(null); e.setScoreDurationMs(null); e.setScoreGrade(null);
        mapScoreValueToEntity(domain.scoreValue(), e);
        e.setCurrentEffective(domain.isCurrentEffective());
        e.setReplacesId(domain.replacesId());
        e.setScoreStatus(domain.status().name());
        e.setSubmittedAt(domain.submittedAt());
        e.setManualMakeup(domain.isManualMakeup());
        e.setUpdatedAt(Instant.now());
    }

    static ScoreAttempt toDomain(ScoreAttemptEntity e) {
        String typeStr = e.getScoreStorageType();
        if (typeStr == null || typeStr.isBlank())
            throw new ScoreValuePersistenceException("scoreStorageType is null or blank for " + e.getId());
        ScoreStorageType type;
        try { type = ScoreStorageType.valueOf(typeStr); }
        catch (IllegalArgumentException ex) { throw new ScoreValuePersistenceException("Unknown scoreStorageType: " + typeStr + " for " + e.getId(), ex); }
        var b = new ScoreAttempt.Builder()
                .id(new ScoreAttemptId(e.getId())).schoolId(e.getSchoolId())
                .activityProjectId(e.getActivityProjectId()).studentId(e.getStudentId())
                .attemptNumber(e.getAttemptNumber())
                .scoreStorageType(type)
                .scoreValue(mapScoreValueFromEntity(e, type))
                .scoreBusinessTime(e.getScoreBusinessTime()).timeSource(e.getTimeSource())
                .replacesId(e.getReplacesId()).enteredBy(e.getEnteredBy());
        return ScoreAttempt.reconstitute(b,
                AttemptStatus.valueOf(e.getScoreStatus()),
                e.isCurrentEffective(), e.getSubmittedAt(), e.isManualMakeup());
    }

    private static ScoreValue mapScoreValueFromEntity(ScoreAttemptEntity e, ScoreStorageType type) {
        return switch (type) {
            case INTEGER -> {
                if (e.getScoreValue() == null)
                    throw new ScoreValuePersistenceException("INTEGER score_value is null for " + e.getId());
                if (e.getScoreDurationMs() != null || e.getScoreGrade() != null)
                    throw new ScoreValuePersistenceException("INTEGER has conflicting fields for " + e.getId());
                long v;
                try { v = e.getScoreValue().longValueExact(); }
                catch (ArithmeticException ex) {
                    throw new ScoreValuePersistenceException("INTEGER score_value not exact long: " + e.getScoreValue() + " for " + e.getId(), ex);
                }
                yield new ScoreValue.IntegerScore(v);
            }
            case DECIMAL -> {
                if (e.getScoreValue() == null)
                    throw new ScoreValuePersistenceException("DECIMAL score_value is null for " + e.getId());
                if (e.getScoreDurationMs() != null || e.getScoreGrade() != null)
                    throw new ScoreValuePersistenceException("DECIMAL has conflicting fields for " + e.getId());
                yield new ScoreValue.DecimalScore(e.getScoreValue());
            }
            case DURATION -> {
                if (e.getScoreDurationMs() == null)
                    throw new ScoreValuePersistenceException("DURATION score_duration_ms is null for " + e.getId());
                if (e.getScoreValue() != null || e.getScoreGrade() != null)
                    throw new ScoreValuePersistenceException("DURATION has conflicting fields for " + e.getId());
                yield new ScoreValue.DurationScore(e.getScoreDurationMs());
            }
            case GRADE -> {
                if (e.getScoreGrade() == null)
                    throw new ScoreValuePersistenceException("GRADE score_grade is null for " + e.getId());
                if (e.getScoreValue() != null || e.getScoreDurationMs() != null)
                    throw new ScoreValuePersistenceException("GRADE has conflicting fields for " + e.getId());
                yield new ScoreValue.GradeScore(e.getScoreGrade());
            }
        };
    }

    private static void mapScoreValueToEntity(ScoreValue v, ScoreAttemptEntity e) {
        // Clear all score fields first to guarantee mutual exclusivity
        e.setScoreValue(null);
        e.setScoreDurationMs(null);
        e.setScoreGrade(null);
        switch (v) {
            case ScoreValue.IntegerScore s -> e.setScoreValue(java.math.BigDecimal.valueOf(s.value()));
            case ScoreValue.DecimalScore s -> e.setScoreValue(s.value());
            case ScoreValue.DurationScore s -> e.setScoreDurationMs(s.durationMs());
            case ScoreValue.GradeScore s -> e.setScoreGrade(s.grade());
        }
    }
}
