package com.campusguinness.interfaces.web.scoreappeal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for correcting and resolving a score appeal (Path A).
 * scoreStorageType discriminates which value field carries the corrected score.
 */
public record CorrectAndResolveScoreAppealRequest(
        @NotBlank String scoreStorageType,
        Long integerValue,
        BigDecimal decimalValue,
        Long durationMs,
        String grade,
        @NotBlank @Size(max = 2000) String resolution
) {}
