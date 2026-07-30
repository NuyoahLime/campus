package com.campusguinness.score.application.command;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateTeacherScoreCommand(
        Long integerValue,
        BigDecimal decimalValue,
        Long durationMs,
        String grade,
        Instant scoreBusinessTime,
        String timeSource) {
}
