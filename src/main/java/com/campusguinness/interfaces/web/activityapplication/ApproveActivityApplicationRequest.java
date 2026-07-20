package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** reviewerId is now sourced from the authenticated SecurityContext via CurrentActor. */
public record ApproveActivityApplicationRequest(
        @NotNull UUID activityId) {}
