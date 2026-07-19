package com.campusguinness.appeal.internal.persistence;

import com.campusguinness.appeal.internal.domain.*;
import java.time.Instant;

final class ScoreAppealPersistenceMapper {
    private ScoreAppealPersistenceMapper() {}

    static ScoreAppealEntity toEntity(ScoreAppeal domain) {
        var e = new ScoreAppealEntity();
        e.setId(domain.id().value()); e.setSchoolId(domain.schoolId());
        e.setScoreAttemptId(domain.scoreAttemptId()); e.setStudentId(domain.studentId());
        e.setAppealType(domain.appealType()); e.setAppealReason(domain.appealReason());
        e.setEvidenceFileKeys(domain.evidenceFileKeys());
        e.setAppealStatus(domain.status().name());
        e.setHandlerId(domain.handlerId()); e.setEscalatedTo(domain.escalatedTo());
        e.setResolution(domain.resolution()); e.setResolvedAt(domain.resolvedAt());
        e.setCreatedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        return e;
    }

    static void updateEntity(ScoreAppealEntity e, ScoreAppeal domain) {
        e.setAppealStatus(domain.status().name());
        e.setHandlerId(domain.handlerId()); e.setEscalatedTo(domain.escalatedTo());
        e.setResolution(domain.resolution()); e.setResolvedAt(domain.resolvedAt());
        e.setUpdatedAt(Instant.now());
    }

    static ScoreAppeal toDomain(ScoreAppealEntity e) {
        return ScoreAppeal.reconstitute(new ScoreAppeal.Builder()
                .id(new ScoreAppealId(e.getId())).schoolId(e.getSchoolId())
                .scoreAttemptId(e.getScoreAttemptId()).studentId(e.getStudentId())
                .appealType(e.getAppealType()).appealReason(e.getAppealReason())
                .evidenceFileKeys(e.getEvidenceFileKeys()),
                AppealStatus.valueOf(e.getAppealStatus()),
                e.getHandlerId(), e.getEscalatedTo(), e.getResolution(), e.getResolvedAt());
    }
}
