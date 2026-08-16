package com.campusguinness.interfaces.web.challengeproject;

import jakarta.validation.constraints.Size;

public record LifecycleReasonRequest(@Size(min = 2, max = 500) String reason) {}
