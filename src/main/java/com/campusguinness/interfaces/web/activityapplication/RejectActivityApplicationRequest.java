package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** TEMPORARY_EXPLICIT_ACTOR_ID */
public record RejectActivityApplicationRequest(@NotNull UUID reviewerId, @NotBlank String reason) {}
