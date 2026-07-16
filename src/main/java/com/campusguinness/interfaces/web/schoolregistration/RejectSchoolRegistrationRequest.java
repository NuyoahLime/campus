package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** TEMPORARY_EXPLICIT_ACTOR_ID */
public record RejectSchoolRegistrationRequest(
        @NotNull UUID reviewerId,
        @NotBlank String reason) {}
