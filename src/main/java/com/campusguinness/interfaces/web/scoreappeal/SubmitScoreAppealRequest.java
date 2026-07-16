package com.campusguinness.interfaces.web.scoreappeal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitScoreAppealRequest(
        @NotNull UUID schoolId,
        @NotNull UUID scoreAttemptId,
        @NotNull UUID studentId,
        @NotBlank String appealType,
        @NotBlank String appealReason) {}
