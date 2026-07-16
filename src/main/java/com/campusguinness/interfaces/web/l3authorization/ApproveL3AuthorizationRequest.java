package com.campusguinness.interfaces.web.l3authorization;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** TEMPORARY_EXPLICIT_ACTOR_ID */
public record ApproveL3AuthorizationRequest(@NotNull UUID reviewerId, String comment) {}
