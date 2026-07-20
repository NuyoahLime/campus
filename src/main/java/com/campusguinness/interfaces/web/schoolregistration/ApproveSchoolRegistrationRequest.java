package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** reviewerId is now sourced from the authenticated SecurityContext via CurrentActor. */
public record ApproveSchoolRegistrationRequest(
        String comment,
        @NotNull UUID schoolId) {}
