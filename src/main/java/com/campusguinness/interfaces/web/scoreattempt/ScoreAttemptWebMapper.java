package com.campusguinness.interfaces.web.scoreattempt;

import com.campusguinness.score.application.command.SubmitScoreCommand;
import com.campusguinness.score.internal.domain.ScoreStorageType;
import com.campusguinness.score.internal.domain.ScoreValue;

import java.math.BigDecimal;

class ScoreAttemptWebMapper {
    private ScoreAttemptWebMapper() {}

    static SubmitScoreCommand toCommand(SubmitScoreRequest req, java.util.UUID actorId) {
        ScoreStorageType type = ScoreStorageType.valueOf(req.scoreStorageType());
        ScoreValue value = toScoreValue(type, req);
        return new SubmitScoreCommand(req.schoolId(), req.activityProjectId(), req.studentId(),
                req.attemptNumber(), type, value, req.scoreBusinessTime(), req.timeSource(), actorId);
    }

    private static ScoreValue toScoreValue(ScoreStorageType type, SubmitScoreRequest req) {
        return switch (type) {
            case INTEGER -> new ScoreValue.IntegerScore(req.integerValue() != null ? req.integerValue() : 0);
            case DECIMAL -> new ScoreValue.DecimalScore(req.decimalValue() != null ? req.decimalValue() : BigDecimal.ZERO);
            case DURATION -> new ScoreValue.DurationScore(req.durationMs() != null ? req.durationMs() : 0);
            case GRADE -> new ScoreValue.GradeScore(req.grade() != null ? req.grade() : "");
        };
    }
}
