package com.campusguinness.interfaces.web.scoreattempt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** schoolId and enteredBy are sourced from server-side Activity and CurrentActor, not the client. */
public record SubmitScoreRequest(
        @NotNull UUID activityProjectId,
        @NotNull UUID studentId,
        @Positive int attemptNumber,
        @NotBlank String scoreStorageType,
        Long integerValue,
        BigDecimal decimalValue,
        @PositiveOrZero Long durationMs,
        String grade,
        Instant scoreBusinessTime,
        String timeSource) {}
