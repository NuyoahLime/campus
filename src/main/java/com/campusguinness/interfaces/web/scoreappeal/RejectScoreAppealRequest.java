package com.campusguinness.interfaces.web.scoreappeal;

import jakarta.validation.constraints.NotBlank;

public record RejectScoreAppealRequest(@NotBlank String resolution) {}
