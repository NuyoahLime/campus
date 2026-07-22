package com.campusguinness.interfaces.web.scoreattempt;

import jakarta.validation.constraints.NotBlank;

public record RejectScoreRequest(@NotBlank String reason) {}
