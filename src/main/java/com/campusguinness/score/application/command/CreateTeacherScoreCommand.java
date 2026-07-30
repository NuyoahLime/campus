package com.campusguinness.score.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateTeacherScoreCommand(
        UUID activityProjectId,
        UUID studentId,
        Long integerValue,
        BigDecimal decimalValue,
        Long durationMs,
        String grade,
        Instant scoreBusinessTime,
        String timeSource) {
}
