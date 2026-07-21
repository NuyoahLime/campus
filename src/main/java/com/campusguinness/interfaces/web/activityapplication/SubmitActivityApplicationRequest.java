package com.campusguinness.interfaces.web.activityapplication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** applicantId is sourced from the authenticated SecurityContext via CurrentActor. */
public record SubmitActivityApplicationRequest(
        @NotNull UUID schoolId,
        @NotBlank @Size(max = 200) String title,
        String description) {}
