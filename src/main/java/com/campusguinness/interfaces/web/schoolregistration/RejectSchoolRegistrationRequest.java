package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** reviewerId is now sourced from the authenticated SecurityContext via CurrentActor. */
public record RejectSchoolRegistrationRequest(
        @NotBlank String reason) {}
