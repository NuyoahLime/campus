package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotBlank;

/** reviewerId is sourced from CurrentActor. */
public record RejectActivityApplicationRequest(@NotBlank String reason) {}
