package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** reviewerId is now sourced from the authenticated SecurityContext via CurrentActor. */
public record RejectActivityApplicationRequest(@NotBlank String reason) {}
