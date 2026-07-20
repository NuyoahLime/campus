package com.campusguinness.interfaces.web.scoreappeal;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** handlerId is now sourced from the authenticated SecurityContext via CurrentActor. */
public record BeginProcessingRequest() {}
