package com.campusguinness.interfaces.web.l3authorization;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** reviewerId is now sourced from the authenticated SecurityContext via CurrentActor. */
public record ApproveL3AuthorizationRequest(String comment) {}
