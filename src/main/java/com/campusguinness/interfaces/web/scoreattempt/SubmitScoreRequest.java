package com.campusguinness.interfaces.web.scoreattempt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** scoreStorageType discriminates which value field carries the actual score. */
public record SubmitScoreRequest(
        @NotNull UUID schoolId,
        @NotNull UUID activityProjectId,
        @NotNull UUID studentId,
        @Positive int attemptNumber,
        @NotBlank String scoreStorageType,
        Long integerValue,
        BigDecimal decimalValue,
        @Positive Long durationMs,
        String grade,
        Instant scoreBusinessTime,
        String timeSource) {}
