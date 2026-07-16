package com.campusguinness.interfaces.web.school;

import jakarta.validation.constraints.NotBlank;

/** TEMPORARY_EXPLICIT_ACTOR_ID */
public record DisableSchoolRequest(@NotBlank String reason) {}
