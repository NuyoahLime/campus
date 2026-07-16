package com.campusguinness.interfaces.web.feedback;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** TEMPORARY_EXPLICIT_ACTOR_ID */
public record BeginProcessingRequest(@NotNull UUID handlerId) {}
