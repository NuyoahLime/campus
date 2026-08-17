package com.campusguinness.interfaces.web.challengeproject;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record LifecycleReasonRequest(@NotBlank @Size(min = 2, max = 500) String reason) {}
