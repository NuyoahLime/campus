package com.campusguinness.interfaces.web.schoolregistration;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** TEMPORARY_EXPLICIT_ACTOR_ID */
public record ApproveSchoolRegistrationRequest(
        @NotNull UUID reviewerId,
        String comment,
        @NotNull UUID schoolId) {}
